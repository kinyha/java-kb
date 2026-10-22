import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;
import reactor.util.retry.Retry;

// Тема Reactor Operators. ./run.sh OperatorsDemo [номер части], без номера — все части.
// Части 2–5 идут на виртуальном времени: задержки точные, вывод одинаковый от запуска к запуску.
public class OperatorsDemo {

    static VirtualTimeScheduler vts;

    public static void main(String[] args) throws Exception {
        List<String> parts = args.length == 0 ? List.of("1", "2", "3", "4", "5", "6", "7") : List.of(args);
        for (String part : parts) {
            vts = VirtualTimeScheduler.getOrSet(true);  // заменяет parallel() и остальные планировщики
            switch (part) {
                case "1" -> mapVsFlatMap();
                case "2" -> ordering();
                case "3" -> combining();
                case "4" -> errors();
                case "5" -> retryAndTimeout();
                case "6" -> caching();
                case "7" -> testing();
                default -> throw new IllegalArgumentException(part);
            }
            VirtualTimeScheduler.reset();
            System.out.println();
        }
    }

    static long t() {                                   // часы того VirtualTimeScheduler, что сейчас установлен
        return VirtualTimeScheduler.isFactoryEnabled() ? VirtualTimeScheduler.get().now(TimeUnit.MILLISECONDS) : 0;
    }

    static void log(String s) { System.out.printf("  t=%-4d %s%n", t(), s); }

    static void run(Flux<?> flux) {
        flux.subscribe(v -> { }, e -> log("onError " + e), () -> { });
        vts.advanceTimeBy(Duration.ofMinutes(10));       // прокрутить виртуальные часы
    }

    // ---------- 1. map и flatMap ----------

    static void mapVsFlatMap() {
        System.out.println("1. map vs flatMap");
        AtomicInteger calls = new AtomicInteger();
        Function<String, Mono<String>> charge = id -> Mono.fromCallable(() -> {
            calls.incrementAndGet();                    // считаем реальные вызовы банка
            return id + " paid";
        });
        Flux.just("p1", "p2")
                .map(charge)                             // Flux<Mono<String>>: Mono никто не подписал
                .subscribe(v -> System.out.println("  map emitted: " + v.getClass().getSimpleName()));
        System.out.println("  bank calls after map: " + calls.get());
        Flux.just("p1", "p2")
                .flatMap(charge)                         // подписывается на каждый Mono и отдаёт его значение
                .subscribe(v -> System.out.println("  flatMap emitted: " + v));
        System.out.println("  bank calls after flatMap: " + calls.get());
    }

    // ---------- 2. Порядок и параллельность ----------

    // Банк отвечает с разной задержкой: p1 — 300 мс, p2 — 100, p3 — 200, p4 — 50.
    static Mono<String> bank(String id) {
        long ms = switch (id) { case "p1" -> 300; case "p2" -> 100; case "p3" -> 200; default -> 50; };
        return Mono.delay(Duration.ofMillis(ms))
                .map(x -> id)
                .doOnSubscribe(s -> log("call " + id));
    }

    static void ordering() {
        System.out.println("2. Order and concurrency: bank latencies p1=300 p2=100 p3=200 p4=50 ms");
        Flux<String> ids = Flux.just("p1", "p2", "p3", "p4");
        System.out.println(" flatMap:");
        run(ids.flatMap(OperatorsDemo::bank).doOnNext(id -> log("   got " + id)));
        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" flatMap(concurrency = 2):");
        run(ids.flatMap(OperatorsDemo::bank, 2).doOnNext(id -> log("   got " + id)));
        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" concatMap:");
        run(ids.concatMap(OperatorsDemo::bank).doOnNext(id -> log("   got " + id)));
        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" flatMapSequential:");
        run(ids.flatMapSequential(OperatorsDemo::bank).doOnNext(id -> log("   got " + id)));
    }

    // ---------- 3. zip, merge, switchIfEmpty ----------

    static <T> Mono<T> after(long ms, String name, T value) {
        return Mono.delay(Duration.ofMillis(ms)).map(x -> value)
                .doOnSubscribe(s -> log("call " + name))
                .doOnCancel(() -> log("cancel " + name));
    }

    static void combining() {
        System.out.println("3. zip, merge, switchIfEmpty");
        System.out.println(" zip, all present:");
        run(Mono.zip(after(100, "accounts", "ACC-1"),
                     after(300, "limits", 1000),
                     after(200, "antifraud", "OK"))
                .doOnNext(t -> log("   got " + t.getT1() + " limit=" + t.getT2() + " fraud=" + t.getT3())).flux());
        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" zip, account not found (empty):");
        run(Mono.zip(after(100, "accounts", "ACC-1").filter(a -> false),   // пустой Mono
                     after(300, "limits", 1000),
                     after(200, "antifraud", "OK"))
                .doOnNext(t -> log("   got " + t))
                .doOnSuccess(t -> { if (t == null) log("   completed empty"); }).flux());
        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" switchIfEmpty, account not found -> 404:");
        run(after(100, "accounts", "ACC-1").filter(a -> false)
                .switchIfEmpty(Mono.error(new IllegalStateException("account not found")))
                .flux());
        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" merge vs concat of two status streams:");
        run(Flux.merge(cardStatuses(), sbpStatuses()).doOnNext(s -> log("   merge " + s)));
        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        run(Flux.concat(cardStatuses(), sbpStatuses()).doOnNext(s -> log("   concat " + s)));
    }

    // interval берёт планировщик при сборке, поэтому источники создаём заново после смены часов
    static Flux<String> cardStatuses() {
        return Flux.interval(Duration.ofMillis(100)).take(3).map(i -> "card-" + i);
    }

    static Flux<String> sbpStatuses() {
        return Flux.interval(Duration.ofMillis(150)).take(2).map(i -> "sbp-" + i);
    }

    // ---------- 4. Ошибки: заменить, перевести; retry и defer ----------

    static final class BankUnavailable extends RuntimeException {
        BankUnavailable() { super("503 from bank"); }
    }

    static final class PaymentFailed extends RuntimeException {
        PaymentFailed(String m, Throwable cause) { super(m, cause); }
    }

    static void errors() {
        System.out.println("4. Errors are terminal: resume, map, and what retry re-runs");
        Flux.just("p1", "p2", "p3")
                .map(id -> { if (id.equals("p2")) throw new BankUnavailable(); return id + " paid"; })
                .subscribe(v -> System.out.println("  got " + v),
                           e -> System.out.println("  onError " + e.getMessage() + ", p3 never processed"));
        Flux.just("p1", "p2", "p3")
                .concatMap(id -> charge(id)
                        .onErrorResume(BankUnavailable.class, e -> Mono.just(id + " PENDING"))) // замена внутри
                .subscribe(v -> System.out.println("  per item: " + v));
        charge("p2")
                .onErrorMap(BankUnavailable.class, e -> new PaymentFailed("payment p2 failed", e))
                .subscribe(v -> { }, e -> System.out.println("  onErrorMap: " + e + " cause=" + e.getCause().getMessage()));

        AtomicInteger futureCalls = new AtomicInteger();
        Mono.fromFuture(bankAsync(futureCalls))           // CompletableFuture создан один раз, при сборке
                .retry(2)
                .subscribe(v -> { }, e -> System.out.println("  fromFuture(cf).retry(2): bank calls = " + futureCalls.get()));
        AtomicInteger supplierCalls = new AtomicInteger();
        Mono.fromFuture(() -> bankAsync(supplierCalls))   // новый вызов на каждую подписку
                .retry(2)
                .subscribe(v -> { }, e -> System.out.println("  fromFuture(() -> cf).retry(2): bank calls = " + supplierCalls.get()));
    }

    static Mono<String> charge(String id) {
        return Mono.fromCallable(() -> {
            if (id.equals("p2")) throw new BankUnavailable();
            return id + " paid";
        });
    }

    static CompletableFuture<String> bankAsync(AtomicInteger calls) {
        calls.incrementAndGet();
        return CompletableFuture.failedFuture(new BankUnavailable());
    }

    // ---------- 5. retryWhen с backoff и timeout ----------

    // Банк: первые failures попыток отвечает 503, потом "paid". hangMs > 0 — попытка висит столько.
    static Mono<String> flakyBank(AtomicInteger attempts, int failures, long hangMs) {
        return Mono.defer(() -> {
            int n = attempts.incrementAndGet();
            log("attempt " + n);
            if (hangMs > 0) return Mono.delay(Duration.ofMillis(hangMs)).map(x -> "paid");
            return n <= failures ? Mono.error(new BankUnavailable()) : Mono.just("paid");
        });
    }

    static void retryAndTimeout() {
        System.out.println("5. retryWhen(Retry.backoff(3, 100ms).jitter(0)) and timeout");
        Retry retry = Retry.backoff(3, Duration.ofMillis(100)).jitter(0)   // jitter 0 — только для воспроизводимости
                .filter(e -> e instanceof BankUnavailable || e instanceof TimeoutException);

        System.out.println(" bank fails twice:");
        run(flakyBank(new AtomicInteger(), 2, 0).retryWhen(retry).doOnNext(v -> log("   got " + v)).flux());

        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" bank always fails:");
        run(flakyBank(new AtomicInteger(), 99, 0).retryWhen(retry).flux());

        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" bank hangs 5 s, timeout(1 s) BEFORE retryWhen: per attempt");
        run(flakyBank(new AtomicInteger(), 0, 5_000).timeout(Duration.ofSeconds(1)).retryWhen(retry).flux());

        VirtualTimeScheduler.reset(); vts = VirtualTimeScheduler.getOrSet(true);
        System.out.println(" bank hangs 5 s, timeout(1 s) AFTER retryWhen: whole operation");
        run(flakyBank(new AtomicInteger(), 0, 5_000).retryWhen(retry).timeout(Duration.ofSeconds(1)).flux());
    }

    // ---------- 6. cache, share, replay, Sinks ----------

    static void caching() {
        System.out.println("6. cache, share, replay, Sinks");
        AtomicInteger tokenCalls = new AtomicInteger();
        Mono<String> token = Mono.fromCallable(() -> {
            int n = tokenCalls.incrementAndGet();
            if (n == 1) throw new IllegalStateException("auth server 503");  // первый вызов падает
            return "token-" + n;
        });
        Mono<String> cachedForever = token.cache();
        cachedForever.subscribe(v -> { }, e -> System.out.println("  cache(): first  -> " + e.getMessage()));
        cachedForever.subscribe(v -> { }, e -> System.out.println("  cache(): second -> " + e.getMessage()
                + ", auth calls = " + tokenCalls.get()));
        tokenCalls.set(0);
        Mono<String> cachedValuesOnly = token.cache(
                v -> Duration.ofMinutes(5),               // значение живёт 5 минут
                e -> Duration.ZERO,                       // ошибку не кешируем
                () -> Duration.ZERO);
        cachedValuesOnly.subscribe(v -> { }, e -> System.out.println("  cache(ttl): first  -> " + e.getMessage()));
        cachedValuesOnly.subscribe(v -> System.out.println("  cache(ttl): second -> " + v));
        cachedValuesOnly.subscribe(v -> System.out.println("  cache(ttl): third  -> " + v
                + ", auth calls = " + tokenCalls.get()));

        System.out.println(" share(): one polling loop for all subscribers");
        Flux<String> statuses = Flux.interval(Duration.ofMillis(100))
                .map(i -> "status-" + i)
                .doOnSubscribe(s -> log("polling started"))
                .doOnCancel(() -> log("polling stopped"))
                .share();
        statuses.take(3).subscribe(s -> log("   A got " + s));
        vts.advanceTimeBy(Duration.ofMillis(150));
        statuses.take(1).subscribe(s -> log("   B got " + s));
        vts.advanceTimeBy(Duration.ofMillis(500));
        statuses.take(1).subscribe(s -> log("   C got " + s));
        vts.advanceTimeBy(Duration.ofMillis(500));

        System.out.println(" replay(1).autoConnect(): late subscriber gets the last value");
        Flux<String> lastStatus = Flux.interval(Duration.ofMillis(100)).map(i -> "status-" + i)
                .take(3).replay(1).autoConnect();
        lastStatus.subscribe(s -> { });
        vts.advanceTimeBy(Duration.ofMillis(500));
        lastStatus.subscribe(s -> log("   late got " + s));

        System.out.println(" Sinks:");
        Sinks.Many<String> direct = Sinks.many().multicast().directBestEffort();
        System.out.println("  multicast().directBestEffort(), no subscribers: " + direct.tryEmitNext("e1"));
        Sinks.Many<String> buffered = Sinks.many().multicast().onBackpressureBuffer();
        System.out.println("  multicast().onBackpressureBuffer(), no subscribers: " + buffered.tryEmitNext("e1"));
        buffered.asFlux().subscribe(s -> System.out.println("  buffered first subscriber got " + s));
        Sinks.Many<String> unicast = Sinks.many().unicast().onBackpressureBuffer();
        unicast.asFlux().subscribe();
        unicast.asFlux().subscribe(s -> { }, e -> System.out.println("  unicast, second subscriber: " + e.getMessage()));
    }

    // ---------- 7. StepVerifier и виртуальное время ----------

    static Mono<String> chargeWithRetry(AtomicInteger attempts) {
        return flakyBank(attempts, 2, 0)
                .retryWhen(Retry.backoff(3, Duration.ofMinutes(1)).jitter(0));  // паузы в минуты
    }

    static void testing() {
        VirtualTimeScheduler.reset();                     // StepVerifier поставит своё виртуальное время
        System.out.println("7. StepVerifier");
        StepVerifier.create(Flux.just("p1", "p3").flatMap(OperatorsDemo::charge))
                .expectNext("p1 paid", "p3 paid")
                .verifyComplete();
        System.out.println("  plain test passed");

        try {
            StepVerifier.create(Flux.just("p1", "p2", "p3").flatMap(OperatorsDemo::charge))
                    .expectNext("p1 paid", "p3 paid")
                    .verifyComplete();
        } catch (AssertionError e) {
            System.out.println("  failed test: " + e.getMessage());
        }

        long start = System.nanoTime();
        AtomicInteger attempts = new AtomicInteger();
        Duration took = StepVerifier.withVirtualTime(() -> chargeWithRetry(attempts)) // Mono создаётся внутри
                .expectSubscription()
                .expectNoEvent(Duration.ofMinutes(1))     // первая пауза: тишина
                .thenAwait(Duration.ofMinutes(2))         // вторая пауза
                .expectNext("paid")
                .verifyComplete();
        System.out.println("  virtual-time test passed: 3 min of backoff, attempts = " + attempts.get()
                + ", real time < 1 s: " + ((System.nanoTime() - start) < 1_000_000_000L));

        Mono<Long> assembledOutside = Mono.delay(Duration.ofMinutes(1)); // взял настоящий parallel() при сборке
        try {
            StepVerifier.withVirtualTime(() -> assembledOutside)
                    .expectSubscription()
                    .thenAwait(Duration.ofMinutes(2))
                    .expectNext(0L)
                    .expectComplete()
                    .verify(Duration.ofSeconds(2));         // без лимита тест ждал бы настоящую минуту
        } catch (AssertionError e) {
            System.out.println("  assembled outside supplier: " + e.getMessage().replaceAll("@\\w+", ""));
        }
    }
}
