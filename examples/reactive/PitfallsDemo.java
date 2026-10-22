import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.context.ContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.blockhound.BlockHound;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.publisher.PublisherProbe;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.tools.agent.ReactorDebugAgent;
import reactor.util.context.Context;

// Тема Reactive Pitfalls. ./run.sh PitfallsDemo <часть>; части запускаются по одной:
// blockhound, debug-hook и debug-agent меняют поведение JVM до её конца.
// blockhound на JDK 13+ требует JAVA_OPTS=-XX:+AllowRedefinitionToAddDeleteMethods.
public class PitfallsDemo {

    static final Logger log = LoggerFactory.getLogger("payments");

    // Один поток, помеченный как неблокирующий, — как поток event loop Netty.
    static final reactor.core.scheduler.Scheduler LOOP = Schedulers.newParallel("event-loop", 1);

    public static void main(String[] args) throws Exception {
        List<String> parts = args.length == 0
                ? List.of("block", "forgotten", "nested", "context", "flatmap", "plain", "checkpoint")
                : List.of(args);
        for (String part : parts) {
            switch (part) {
                case "block" -> blockInNonBlockingThread();
                case "blockhound" -> blockHound();
                case "forgotten" -> forgottenSubscription();
                case "nested" -> nestedSubscribe();
                case "context" -> context();
                case "flatmap" -> unboundedFlatMap();
                case "plain" -> stackTraces("plain");
                case "checkpoint" -> stackTraces("checkpoint");
                case "debug-hook" -> stackTraces("debug-hook");
                case "debug-agent" -> stackTraces("debug-agent");
                default -> throw new IllegalArgumentException(part);
            }
            System.out.println();
        }
        System.exit(0);
    }

    // ---------- block() в неблокирующем потоке ----------

    static Mono<String> bankSlow() { return Mono.delay(Duration.ofMillis(100)).map(x -> "paid"); }

    static Mono<String> bankReady() { return Mono.just("paid"); }

    static void blockInNonBlockingThread() throws Exception {
        System.out.println("block: block() inside a non-blocking thread");
        CountDownLatch done = new CountDownLatch(2);
        Mono.delay(Duration.ofMillis(10), LOOP)          // дальше цепочка идёт в потоке event-loop-1
                .map(x -> bankReady().block())          // значение уже готово: ждать не нужно
                .subscribe(v -> { System.out.println("  ready Mono: block() returned " + v); done.countDown(); });
        Thread.sleep(50);
        Mono.delay(Duration.ofMillis(10), LOOP)
                .map(x -> bankSlow().block())           // значение надо ждать
                .subscribe(v -> { }, e -> { System.out.println("  slow Mono: " + e); done.countDown(); });
        done.await();
    }

    // ---------- BlockHound ----------

    static String jdbcFindAccount() {
        try { Thread.sleep(20); } catch (InterruptedException e) { throw new RuntimeException(e); } // как ожидание JDBC
        return "ACC-1";
    }

    static void blockHound() throws Exception {
        System.out.println("blockhound: blocking calls on non-blocking threads");
        BlockHound.install();
        check("map(x -> jdbc())",
                Mono.delay(Duration.ofMillis(10), LOOP).map(x -> jdbcFindAccount()));
        check("flatMap(x -> Mono.just(jdbc()).subscribeOn(boundedElastic))",
                Mono.delay(Duration.ofMillis(10), LOOP)
                        .flatMap(x -> Mono.just(jdbcFindAccount())             // аргумент считается здесь
                                .subscribeOn(Schedulers.boundedElastic())));
        check("flatMap(x -> Mono.fromCallable(jdbc).subscribeOn(boundedElastic))",
                Mono.delay(Duration.ofMillis(10), LOOP)
                        .flatMap(x -> Mono.fromCallable(PitfallsDemo::jdbcFindAccount)
                                .subscribeOn(Schedulers.boundedElastic())));
    }

    static void check(String name, Mono<String> mono) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        mono.subscribe(v -> { System.out.println("  " + name + " -> " + v + " on " + Thread.currentThread().getName()); done.countDown(); },
                       e -> { System.out.println("  " + name + " -> " + e); done.countDown(); });
        done.await(5, TimeUnit.SECONDS);
    }

    // ---------- Забытая подписка ----------

    static final AtomicInteger saved = new AtomicInteger();

    static Mono<Void> save(String payment) {                // реактивный репозиторий: работа при подписке
        return Mono.fromRunnable(saved::incrementAndGet);
    }

    static Mono<String> notifyClient(String payment) { return Mono.just(payment + " notified"); }

    static void forgottenSubscription() {
        System.out.println("forgotten: Mono that nobody subscribed to");
        Mono.just("p1").doOnNext(p -> save(p)).block();                          // Mono из save выброшен
        System.out.println("  doOnNext(p -> save(p)):            saved = " + saved.getAndSet(0));
        Mono.just("p1").flatMap(p -> { save(p); return notifyClient(p); }).block(); // тоже выброшен
        System.out.println("  flatMap { save(p); return notify }: saved = " + saved.getAndSet(0));
        Mono.just("p1").flatMap(p -> save(p).then(notifyClient(p))).block();      // встроен в цепочку
        System.out.println("  flatMap(p -> save(p).then(notify)): saved = " + saved.getAndSet(0));

        PublisherProbe<Void> probe = PublisherProbe.empty();                       // подмена save в тесте
        Mono.just("p1").doOnNext(p -> probe.mono()).block();
        try {
            probe.assertWasSubscribed();
        } catch (AssertionError e) {
            System.out.println("  test with PublisherProbe: " + e.getMessage());
        }
    }

    // ---------- subscribe() внутри flatMap ----------

    static Mono<Void> audit(String payment, long ms, boolean fail) {
        return Mono.deferContextual(ctx -> {
            String user = ctx.getOrDefault("user", "anonymous");
            return Mono.delay(Duration.ofMillis(ms))
                    .doOnNext(x -> { if (fail) throw new IllegalStateException("audit DB down"); })
                    .doOnNext(x -> vlog("audit saved " + payment + " for user " + user))
                    .then();
        });
    }

    static Mono<String> bank(String payment) {
        return Mono.delay(Duration.ofMillis(50)).map(x -> payment + " paid")
                .doOnCancel(() -> vlog("bank call cancelled"));
    }

    static long now() { return VirtualTimeScheduler.get().now(TimeUnit.MILLISECONDS); }

    static void vlog(String s) { System.out.printf("  t=%-4d %s%n", now(), s); }

    static VirtualTimeScheduler fresh(VirtualTimeScheduler vts) { // доиграть сценарий и начать часы с нуля
        vts.advanceTimeBy(Duration.ofSeconds(1));
        VirtualTimeScheduler.reset();
        return VirtualTimeScheduler.getOrSet(true);
    }

    static void nestedSubscribe() {
        System.out.println("nested: subscribe() inside flatMap");
        VirtualTimeScheduler vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" fire-and-forget, audit fails after 200 ms:");
        Mono.just("p1")
                .flatMap(p -> {
                    audit(p, 200, true).subscribe();                      // отдельная подписка, без обработчика ошибок
                    return bank(p);
                })
                .contextWrite(Context.of("user", "alice"))               // контекст запроса
                .subscribe(v -> vlog("response: " + v), e -> vlog("response error: " + e.getMessage()));
        vts = fresh(vts);

        System.out.println(" fire-and-forget, audit ok, user from context:");
        Mono.just("p1")
                .flatMap(p -> { audit(p, 200, false).subscribe(); return bank(p); })
                .contextWrite(Context.of("user", "alice"))
                .subscribe(v -> vlog("response: " + v));
        vts = fresh(vts);

        System.out.println(" fire-and-forget, client disconnects at 20 ms:");
        Disposable request = Mono.just("p1")
                .flatMap(p -> { audit(p, 200, false).subscribe(); return bank(p); })
                .contextWrite(Context.of("user", "alice"))
                .subscribe(v -> vlog("response: " + v));
        vts.advanceTimeBy(Duration.ofMillis(20));
        request.dispose();                                               // как обрыв соединения в WebFlux
        vlog("client gone");
        vts = fresh(vts);

        System.out.println(" in the chain: audit(p).then(bank(p)):");
        Mono.just("p1")
                .flatMap(p -> audit(p, 200, true).then(bank(p)))
                .contextWrite(Context.of("user", "alice"))
                .subscribe(v -> vlog("response: " + v), e -> vlog("response error: " + e.getMessage()));
        vts = fresh(vts);
        System.out.println(" in the chain: audit ok:");
        Mono.just("p1")
                .flatMap(p -> audit(p, 200, false).then(bank(p)))
                .contextWrite(Context.of("user", "alice"))
                .subscribe(v -> vlog("response: " + v));
        vts.advanceTimeBy(Duration.ofSeconds(1));
        VirtualTimeScheduler.reset();
    }

    // ---------- Контекст ----------

    static Mono<String> currentUser() {                     // как ReactiveSecurityContextHolder.getContext()
        return Mono.deferContextual(ctx -> Mono.just(ctx.getOrDefault("user", "anonymous")));
    }

    static void context() throws Exception {
        System.out.println("context: Context flows from subscriber up to source");
        String below = currentUser()
                .contextWrite(Context.of("user", "alice"))      // ниже чтения: видно
                .block();
        String above = Mono.just("p1")
                .contextWrite(Context.of("user", "alice"))      // выше чтения: не видно
                .flatMap(p -> currentUser())
                .block();
        System.out.println("  contextWrite below the read: " + below);
        System.out.println("  contextWrite above the read: " + above);

        System.out.println(" MDC without propagation:");
        MDC.put("traceId", "t-42");                             // фильтр положил traceId в MDC потока запроса
        log.info("request accepted");
        Mono.just("p1")
                .publishOn(LOOP)
                .doOnNext(p -> log.info("charging " + p))       // другой поток: MDC пуст
                .block();
        MDC.clear();

        System.out.println(" MDC with Hooks.enableAutomaticContextPropagation():");
        ContextRegistry.getInstance().registerThreadLocalAccessor("traceId",
                () -> MDC.get("traceId"), v -> MDC.put("traceId", v), () -> MDC.remove("traceId"));
        Hooks.enableAutomaticContextPropagation();
        Mono.just("p1")
                .publishOn(LOOP)
                .doOnNext(p -> log.info("charging " + p))       // значение из Context вернулось в MDC
                .contextWrite(Context.of("traceId", "t-42"))
                .block();
        Hooks.disableAutomaticContextPropagation();
    }

    // ---------- Неограниченный flatMap ----------

    static final class Bank {
        final int limit;
        int inFlight, peak, rejected, served;
        Bank(int limit) { this.limit = limit; }

        Mono<String> charge(String id) {                    // банк держит не больше limit запросов
            return Mono.defer(() -> {
                if (inFlight >= limit) { rejected++; return Mono.error(new IllegalStateException("429")); }
                inFlight++; peak = Math.max(peak, inFlight);
                return Mono.delay(Duration.ofMillis(100))
                        .doOnNext(x -> { inFlight--; served++; }) // банк ответил
                        .map(x -> id);
            });
        }
    }

    static void unboundedFlatMap() {
        System.out.println("flatmap: 1000 payments, bank accepts 20 concurrent requests, 100 ms each");
        run(256, "flatMap(charge)        ");
        run(16, "flatMap(charge, 16)    ");
    }

    static void run(int concurrency, String name) {
        VirtualTimeScheduler vts = VirtualTimeScheduler.getOrSet(true);
        Bank bank = new Bank(20);
        long[] end = new long[1];
        Flux.range(1, 1000)
                .flatMap(i -> bank.charge("p" + i)
                        .onErrorResume(e -> Mono.just("rejected")), concurrency)
                .doOnComplete(() -> end[0] = now())
                .subscribe();
        vts.advanceTimeBy(Duration.ofMinutes(1));
        System.out.printf("  %s peak at bank = %d, rejected with 429 = %d, served = %d, took %d ms%n",
                name, bank.peak, bank.rejected, bank.served, end[0]);
        VirtualTimeScheduler.reset();
    }

    // ---------- Стектрейсы ----------

    static Mono<String> findPayment(String id) {
        return Flux.just(id, id + "-duplicate")               // баг: запрос вернул две строки
                .single();                                      // ждём ровно одну
    }

    static void stackTraces(String mode) throws Exception {
        switch (mode) {
            case "debug-hook" -> Hooks.onOperatorDebug();
            case "debug-agent" -> { ReactorDebugAgent.init(); ReactorDebugAgent.processExistingClasses(); }
            default -> { }
        }
        System.out.println(mode + ": the same failing chain");
        CountDownLatch done = new CountDownLatch(1);
        Mono<String> chain = Mono.delay(Duration.ofMillis(10))
                .flatMap(x -> findPayment("p1"))
                .map(String::toUpperCase);
        if (mode.equals("checkpoint")) chain = chain.checkpoint("load payment for refund");
        chain.subscribe(v -> { }, e -> {
            var text = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(text));           // как стектрейс в логе
            List<String> lines = text.toString().lines().toList();
            int shown = Math.min(lines.size(), mode.equals("plain") ? 8 : 12);
            lines.subList(0, shown).forEach(l -> System.out.println("  " + l));
            System.out.println("  ... " + (lines.size() - shown) + " more lines");
            done.countDown();
        });
        done.await();
    }
}
