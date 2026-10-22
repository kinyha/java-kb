import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

// Тема Reactive Philosophy. ./run.sh PhilosophyDemo [номер части], без номера — все части.
public class PhilosophyDemo {

    public static void main(String[] args) throws Exception {
        List<String> parts = args.length == 0
                ? List.of("1", "2", "3", "4", "5", "6", "7", "8", "9")
                : List.of(args);
        for (String part : parts) {
            switch (part) {
                case "1" -> pushWithoutDemand();
                case "2" -> pullFromCursor();
                case "3" -> recursion();
                case "4" -> ruleViolations();
                case "5" -> demandThroughChain();
                case "6" -> hotSourceOverflow();
                case "7" -> assemblyVsSubscription();
                case "8" -> coldVsHot();
                case "9" -> submissionPublisher();
                default -> throw new IllegalArgumentException(part);
            }
        }
        System.exit(0);
    }

    // ---------- 1. Асинхронно, но без спроса: всё копится в памяти ----------

    static void pushWithoutDemand() throws Exception {
        System.out.println("1. Push without demand: async callback into a slow client");
        var client = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>());   // очередь без границы, как у newFixedThreadPool
        CountDownLatch clientBusy = new CountDownLatch(1);
        AtomicInteger sent = new AtomicInteger();
        client.execute(() -> await(clientBusy));          // клиент медленный: пока занят
        for (int row = 1; row <= 100_000; row++) {        // курсор БД отдаёт строки
            client.execute(() -> sent.incrementAndGet()); // «отправить клиенту» — задача в очередь
        }
        int queued = client.getQueue().size();
        System.out.println("  rows read from DB: 100000, sent to client: " + sent.get()
                + ", waiting in memory: " + queued);
        clientBusy.countDown();
        client.shutdown();
        client.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }

    // ---------- 2. Свой Publisher на Flow: строки читаются только по запросу ----------

    // Курсор БД: считаем, сколько строк реально прочитано.
    static final class Cursor {
        final int size;
        int read;
        Cursor(int size) { this.size = size; }
        boolean hasNext() { return read < size; }
        String next() { read++; return "payment-" + read; }
    }

    // Издатель строк выписки. Правильная версия: спрос копится в счётчике,
    // а отдаёт строки один цикл drain, поэтому request() из onNext не углубляет стек.
    static final class PaymentsPublisher implements Flow.Publisher<String> {
        final Cursor cursor;
        PaymentsPublisher(Cursor cursor) { this.cursor = cursor; }

        public void subscribe(Flow.Subscriber<? super String> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                final AtomicLong demand = new AtomicLong();     // сколько ещё можно отдать
                final AtomicInteger wip = new AtomicInteger();  // кто-то уже в цикле drain?
                volatile boolean cancelled;
                boolean done;

                public void request(long n) {
                    if (n <= 0) {                                // правило 3.9
                        cancelled = true;
                        subscriber.onError(new IllegalArgumentException("§3.9: n must be > 0, got " + n));
                        return;
                    }
                    demand.getAndAccumulate(n, (a, b) -> a + b < 0 ? Long.MAX_VALUE : a + b); // 3.17
                    drain();
                }

                public void cancel() { cancelled = true; }

                void drain() {
                    if (wip.getAndIncrement() != 0) return;     // цикл уже крутится выше по стеку
                    do {
                        while (demand.get() > 0 && !cancelled && cursor.hasNext()) {
                            demand.decrementAndGet();
                            subscriber.onNext(cursor.next());   // не больше, чем запросили: 1.1
                        }
                        if (!cursor.hasNext() && !cancelled && !done) {
                            done = true;
                            subscriber.onComplete();            // после этого — ни одного сигнала
                        }
                    } while (wip.decrementAndGet() != 0);
                }
            });
        }
    }

    static void pullFromCursor() {
        System.out.println("2. Own Flow.Publisher: rows are read only on demand");
        Cursor cursor = new Cursor(1_000_000);
        new PaymentsPublisher(cursor).subscribe(new Flow.Subscriber<>() {
            Flow.Subscription subscription;
            int received;

            public void onSubscribe(Flow.Subscription s) {
                subscription = s;
                System.out.println("  onSubscribe, rows read: " + cursor.read);
                s.request(2);                                   // клиент готов принять две строки
            }

            public void onNext(String row) {
                received++;
                System.out.println("  onNext(" + row + "), rows read: " + cursor.read);
                if (received == 2) subscription.request(3);              // отправил две — прошу ещё три
                if (received == 5) {
                    System.out.println("  client disconnected -> cancel()");
                    subscription.cancel();
                }
            }

            public void onError(Throwable t) { System.out.println("  onError " + t); }
            public void onComplete() { System.out.println("  onComplete"); }
        });
        System.out.println("  rows read from DB: " + cursor.read + " of " + cursor.size);
        System.out.println();
    }

    // ---------- 3. Наивный издатель: request(1) из onNext уходит в рекурсию ----------

    static final class NaivePublisher implements Flow.Publisher<Integer> {
        final int size;
        NaivePublisher(int size) { this.size = size; }

        public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                int next;
                public void request(long n) {                   // отдаём прямо в request
                    for (long i = 0; i < n && next < size; i++) {
                        subscriber.onNext(++next);              // onNext вызовет request(1) — глубже по стеку
                    }
                    if (next == size) subscriber.onComplete();
                }
                public void cancel() { }
            });
        }
    }

    // Подписчик, который, как BaseSubscriber с request(1), просит по одному.
    static Flow.Subscriber<Integer> oneByOne(String name, AtomicInteger count, CountDownLatch done) {
        return new Flow.Subscriber<>() {
            Flow.Subscription s;
            public void onSubscribe(Flow.Subscription s) { this.s = s; s.request(1); }
            public void onNext(Integer item) { count.incrementAndGet(); s.request(1); }
            public void onError(Throwable t) { System.out.println("  " + name + ": onError " + t); done.countDown(); }
            public void onComplete() { System.out.println("  " + name + ": onComplete after " + count.get()); done.countDown(); }
        };
    }

    static void recursion() {
        System.out.println("3. request(1) from onNext: naive publisher vs drain loop");
        AtomicInteger naive = new AtomicInteger();
        try {
            new NaivePublisher(100_000).subscribe(oneByOne("naive", naive, new CountDownLatch(1)));
        } catch (StackOverflowError e) {
            System.out.println("  naive: StackOverflowError after " + naive.get() + " items");
        }
        AtomicInteger fixed = new AtomicInteger();
        Cursor cursor = new Cursor(100_000);
        new PaymentsPublisher(cursor).subscribe(new Flow.Subscriber<>() {
            Flow.Subscription s;
            public void onSubscribe(Flow.Subscription s) { this.s = s; s.request(1); }
            public void onNext(String item) { fixed.incrementAndGet(); s.request(1); }
            public void onError(Throwable t) { System.out.println("  drain loop: onError " + t); }
            public void onComplete() { System.out.println("  drain loop: onComplete after " + fixed.get()); }
        });
        System.out.println();
    }

    // ---------- 4. Нарушения правил ловит сама библиотека ----------

    static void ruleViolations() {
        System.out.println("4. Rule violations: Reactor turns them into onError");
        Flux.range(1, 3).subscribe(new org.reactivestreams.Subscriber<Integer>() { // «чужой» подписчик
            public void onSubscribe(org.reactivestreams.Subscription s) { s.request(0); } // нарушает 3.9
            public void onNext(Integer i) { System.out.println("  got " + i); }
            public void onError(Throwable t) { System.out.println("  request(0), plain Subscriber: " + t); }
            public void onComplete() { }
        });
        Flux.range(1, 3).subscribe(new BaseSubscriber<Integer>() {                  // подписчик Reactor
            protected void hookOnSubscribe(org.reactivestreams.Subscription s) { request(0); }
            protected void hookOnNext(Integer i) { System.out.println("  got " + i); }
            protected void hookOnError(Throwable t) { System.out.println("  request(0), BaseSubscriber: " + t); }
        });
        System.out.println("  BaseSubscriber: no signal at all");
        Flux.just("a", "b")
                .map(s -> s.equals("b") ? (String) null : s)    // null в onNext запрещён: 2.13
                .subscribe(s -> System.out.println("  got " + s),
                           t -> System.out.println("  null: " + t));
        System.out.println();
    }

    // ---------- 5. Спрос едет вверх по цепочке ----------

    static void demandThroughChain() throws Exception {
        System.out.println("5. Demand travels up the chain");
        System.out.println("  subscribe(lambda):");
        Flux.range(1, 3)
                .doOnRequest(n -> System.out.println("    source got request(" + n + ")"))
                .subscribe(i -> { });                           // лямбда = неограниченный спрос

        System.out.println("  publishOn, prefetch 8, subscriber takes 20:");
        CountDownLatch done = new CountDownLatch(1);
        Flux.range(1, 20)
                .doOnRequest(n -> System.out.println("    source got request(" + n + ")"))
                .publishOn(Schedulers.single(), 8)              // очередь на 8 элементов между потоками
                .subscribe(i -> { }, e -> { }, done::countDown);
        done.await();

        System.out.println("  limitRate(10) under unbounded subscriber, 25 items:");
        Flux.range(1, 25)
                .doOnRequest(n -> System.out.println("    source got request(" + n + ")"))
                .limitRate(10)
                .subscribe(i -> { });
        System.out.println();
    }

    // ---------- 6. Источник, который не умеет ждать ----------

    static void hotSourceOverflow() throws Exception {
        System.out.println("6. A source that cannot wait: Flux.interval and a stuck consumer");
        CountDownLatch done = new CountDownLatch(1);
        Flux.interval(Duration.ofMillis(1))                     // тикает по таймеру, ждать не умеет
                .publishOn(Schedulers.single(), 16)             // очередь на 16 тиков
                .subscribe(tick -> sleep(5_000),                // потребитель завис на первом тике
                           e -> { System.out.println("  onError: " + e); done.countDown(); });
        done.await();
        System.out.println();
    }

    // ---------- 7. Сборка и подписка ----------

    static String charge(String who) {
        System.out.println("    bank.charge() called by " + who);
        return "paid";
    }

    static void assemblyVsSubscription() {
        System.out.println("7. Assembly vs subscription");
        System.out.println("  assemble Mono.just(charge()):");
        Mono<String> eager = Mono.just(charge("just"));         // аргумент вычисляется сразу, до подписки
        System.out.println("  assemble Mono.fromCallable(charge):");
        Mono<String> lazy = Mono.fromCallable(() -> charge("fromCallable"));
        System.out.println("  nobody subscribed. Now subscribe twice to each:");
        eager.subscribe(); eager.subscribe();
        lazy.subscribe(); lazy.subscribe();
        System.out.println("  found in cache, fallback via switchIfEmpty(Mono.just(charge())):");
        Mono.just("cached").switchIfEmpty(Mono.just(charge("fallback")))  // аргумент считается при сборке
                .subscribe(v -> System.out.println("    result: " + v));
        System.out.println();
    }

    // ---------- 8. Cold и hot ----------

    static void coldVsHot() {
        System.out.println("8. Cold vs hot");
        AtomicInteger queries = new AtomicInteger();
        Flux<String> cold = Flux.defer(() -> {
            queries.incrementAndGet();                          // каждый подписчик — свой запрос в БД
            return Flux.just("p1", "p2", "p3");
        });
        cold.subscribe(p -> System.out.println("  cold A got " + p));
        cold.subscribe(p -> System.out.println("  cold B got " + p));
        System.out.println("  cold: DB queries = " + queries.get());

        Sinks.Many<String> statuses = Sinks.many().multicast().directBestEffort();
        Flux<String> hot = statuses.asFlux();                   // события идут независимо от подписчиков
        statuses.tryEmitNext("p1 NEW");                         // никого нет — событие пропало
        hot.subscribe(s -> System.out.println("  hot A got " + s));
        statuses.tryEmitNext("p1 PAID");
        hot.subscribe(s -> System.out.println("  hot B got " + s));
        statuses.tryEmitNext("p2 NEW");
        System.out.println();
    }

    // ---------- 9. SubmissionPublisher из JDK ----------

    static void submissionPublisher() throws Exception {
        System.out.println("9. SubmissionPublisher: submit() blocks when the subscriber is behind");
        ExecutorService delivery = Executors.newSingleThreadExecutor();
        List<String> log = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>(delivery, 4)) {
            publisher.subscribe(new Flow.Subscriber<>() {
                Flow.Subscription s;
                public void onSubscribe(Flow.Subscription s) { this.s = s; s.request(1); }
                public void onNext(Integer item) {
                    sleep(100);                                  // медленный клиент
                    synchronized (log) { log.add("consumed " + item); }
                    s.request(1);
                }
                public void onError(Throwable t) { }
                public void onComplete() { done.countDown(); }
            });
            for (int i = 1; i <= 8; i++) {
                long start = System.nanoTime();
                publisher.submit(i);                             // блокирует, если буфер полон
                long waited = (System.nanoTime() - start) / 50_000_000; // в шагах по 50 мс
                synchronized (log) { log.add("submitted " + i + (waited > 0 ? " after waiting" : "")); }
            }
        }
        done.await();
        log.forEach(l -> System.out.println("  " + l));
        delivery.shutdown();
        System.out.println();
    }

    // ---------- обвязка ----------

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
