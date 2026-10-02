import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// Запуск: java -cp "$(cat target/classpath.txt)" ResilienceDemo.java   (JDK 21, Resilience4j 2.4)
public class ResilienceDemo {
    public static void main(String[] args) throws Exception {
        cascade(false);
        cascade(true);
        retryAmplification();
        jitter();
        circuitBreaker();
    }

    // ---------- каскад: медленный банк занимает общий пул, страдает и быстрый эндпоинт ----------
    static void cascade(boolean bulkhead) throws Exception {
        ExecutorService tomcat = Executors.newFixedThreadPool(10);        // 10 потоков обработки запросов.
        Bulkhead bh = Bulkhead.of("bank", BulkheadConfig.custom().maxConcurrentCalls(4).maxWaitDuration(Duration.ZERO).build());
        Supplier<String> slowBank = () -> { sleep(2000); return "charged"; };   // Банк завис на 2 с.
        List<Future<String>> pays = new ArrayList<>();
        for (int i = 0; i < 20; i++) pays.add(tomcat.submit(() -> {
            try { return bulkhead ? bh.executeSupplier(slowBank) : slowBank.get(); }
            catch (BulkheadFullException e) { return "503 fast"; }
        }));
        Thread.sleep(100);
        long t = System.nanoTime();
        tomcat.submit(() -> "status ok").get();                         // Быстрый запрос статуса.
        long statusMs = (System.nanoTime() - t) / 1_000_000;
        int rejected = 0;
        for (var f : pays) if (f.get().startsWith("503")) rejected++;
        System.out.printf("== 20 calls to a hung bank, %s: status request waited %d ms, bank calls rejected fast: %d%n",
                bulkhead ? "bulkhead 4" : "no bulkhead", statusMs, rejected);
        tomcat.shutdown();
    }

    // ---------- повторы на каждом уровне перемножаются ----------
    static void retryAmplification() {
        AtomicInteger bankCalls = new AtomicInteger();
        RetryConfig cfg = RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1)).build();
        Supplier<String> bank = () -> { bankCalls.incrementAndGet(); throw new RuntimeException("503"); };
        Supplier<String> payments = Retry.decorateSupplier(Retry.of("payments->bank", cfg), bank);
        Supplier<String> checkout = Retry.decorateSupplier(Retry.of("checkout->payments", cfg), payments);
        Supplier<String> gateway = Retry.decorateSupplier(Retry.of("gateway->checkout", cfg), checkout);
        try { gateway.get(); } catch (RuntimeException ignored) { }
        System.out.println("== one user request, 3 attempts at each of 3 layers: bank received " + bankCalls.get() + " calls");
    }

    // ---------- backoff без jitter собирает повторы в пики ----------
    static void jitter() {
        Random rnd = new Random(42);
        int clients = 1000;
        for (boolean withJitter : new boolean[] {false, true}) {
            TreeMap<Integer, Integer> per100ms = new TreeMap<>();
            for (int c = 0; c < clients; c++) {
                double t = 0;
                for (int attempt = 1; attempt <= 4; attempt++) {          // 4 повтора после общего сбоя в момент 0.
                    double backoff = Math.pow(2, attempt - 1);            // 1, 2, 4, 8 с
                    t += withJitter ? rnd.nextDouble() * backoff : backoff;   // full jitter: случайно от 0 до backoff
                    per100ms.merge((int) (t * 10), 1, Integer::sum);
                }
            }
            int peak = per100ms.values().stream().max(Integer::compare).orElse(0);
            System.out.printf("== 1000 clients, backoff %-16s peak %4d retries per 100 ms, busy 100-ms windows: %d%n",
                    withJitter ? "with full jitter" : "without jitter", peak, per100ms.size());
        }
    }

    // ---------- circuit breaker: открыт → быстрый отказ → полуоткрыт → закрыт ----------
    static void circuitBreaker() throws Exception {
        CircuitBreaker cb = CircuitBreaker.of("bank", CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(10).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(1)).permittedNumberOfCallsInHalfOpenState(3).build());
        List<String> transitions = new ArrayList<>();
        cb.getEventPublisher().onStateTransition(e -> transitions.add(e.getStateTransition().toString()));
        AtomicInteger bankCalls = new AtomicInteger();
        boolean[] bankUp = {false};
        Supplier<String> bank = () -> {
            bankCalls.incrementAndGet();
            if (!bankUp[0]) { sleep(200); throw new RuntimeException("timeout"); }   // Каждый неудачный вызов ждёт 200 мс.
            return "ok";
        };
        int fastRejects = 0;
        long t = System.nanoTime();
        for (int i = 0; i < 30; i++) {
            try { cb.executeSupplier(bank); }
            catch (CallNotPermittedException e) { fastRejects++; }
            catch (RuntimeException ignored) { }
        }
        System.out.printf("== 30 calls to a failing bank: bank got %d, rejected without calling %d, took %d ms, state %s%n",
                bankCalls.get(), fastRejects, (System.nanoTime() - t) / 1_000_000, cb.getState());
        bankUp[0] = true;
        Thread.sleep(1100);
        for (int i = 0; i < 3; i++) cb.executeSupplier(bank);
        System.out.println("   bank recovered, 3 trial calls -> state " + cb.getState() + "; transitions " + transitions);
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
