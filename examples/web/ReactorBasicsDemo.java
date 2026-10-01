import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

// Mono и Flux: ленивость, потоки, контекст и backpressure. Без сервера.
public class ReactorBasicsDemo {
    static final ThreadLocal<String> USER = new ThreadLocal<>();

    static String thread() {
        return Thread.currentThread().getName();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. Nothing happens until subscribe");
        Mono<String> charge = Mono.fromCallable(() -> {
            System.out.println("  bank called on " + thread());
            return "paid";
        });
        System.out.println("  Mono created");
        charge.subscribe(result -> System.out.println("  first subscriber got " + result));
        charge.subscribe(result -> System.out.println("  second subscriber got " + result)); // вторая подписка — второй вызов

        System.out.println("2. Operators run on the thread the signal arrives on");
        Flux.range(1, 2)
                .map(i -> { System.out.println("  map " + i + " on " + thread()); return i; })
                .publishOn(Schedulers.parallel())                // всё ниже — в потоке parallel
                .map(i -> { System.out.println("  after publishOn " + i + " on " + thread()); return i; })
                .subscribeOn(Schedulers.boundedElastic())        // откуда стартует подписка, то есть начало цепочки
                .blockLast();                                    // только в демо: ждём конца

        System.out.println("3. ThreadLocal is lost, Reactor Context travels with the signal");
        USER.set("alice");
        String viaThreadLocal = Mono.just("payment")
                .publishOn(Schedulers.parallel())
                .map(p -> p + " by " + USER.get())               // другой поток: ThreadLocal пуст
                .block();
        String viaContext = Mono.just("payment")
                .publishOn(Schedulers.parallel())
                .flatMap(p -> Mono.deferContextual(ctx -> Mono.just(p + " by " + ctx.get("user"))))
                .contextWrite(Context.of("user", "alice"))       // контекст едет вместе с подпиской
                .block();
        System.out.println("  ThreadLocal: " + viaThreadLocal);
        System.out.println("  Context:     " + viaContext);

        System.out.println("4. Backpressure: the subscriber asks for 2 at a time");
        Flux.range(1, 6)
                .doOnRequest(n -> System.out.println("  request(" + n + ")"))
                .doOnNext(i -> System.out.println("  emit " + i))
                .subscribe(new BaseSubscriber<Integer>() {
                    int handled;

                    @Override
                    protected void hookOnSubscribe(Subscription subscription) {
                        request(2);                              // готов принять только два элемента
                    }

                    @Override
                    protected void hookOnNext(Integer value) {
                        if (++handled % 2 == 0) request(2);      // обработал два — прошу ещё два
                    }
                });
    }
}
