import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: java OverloadDemo.java   (JDK 21)
// Сервер: 8 потоков по 10 мс на запрос — около 800 в секунду в теории; sleep(10) длится дольше, на деле ~620.
// Клиенты: открытый поток 1 200 запросов в секунду на 5 секунд, тайм-аут клиента 300 мс.
public class OverloadDemo {
    static final int RATE = 1200, SECONDS = 5, TIMEOUT_MS = 300;

    enum Mode { UNBOUNDED, SHED, DEADLINE, QUEUE_TIME }

    public static void main(String[] args) throws Exception {
        System.out.println("== offered 1200 req/s for 5 s to 8 workers x 10 ms, client timeout 300 ms");
        run(Mode.UNBOUNDED, 0, 8101);
        run(Mode.SHED, 0, 8102);
        run(Mode.DEADLINE, 0, 8103);
        run(Mode.QUEUE_TIME, 0, 8105);
        run(Mode.UNBOUNDED, 2, 8104);
        System.exit(0);
    }

    static void run(Mode mode, int retries, int port) throws Exception {
        AtomicInteger worked = new AtomicInteger(), wasted = new AtomicInteger(), rejected = new AtomicInteger();
        BlockingQueue<Runnable> queue = mode == Mode.SHED ? new ArrayBlockingQueue<>(40) : new LinkedBlockingQueue<>();
        var pool = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS, queue);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 4096);
        server.setExecutor(Runnable::run);                    // приём — в потоке сервера, работа — в нашем пуле
        server.createContext("/", ex -> {
            long arrived = System.nanoTime();
            try {
                pool.execute(() -> {
                    try {
                        long waited = (System.nanoTime() - arrived) / 1_000_000;
                        long limit = mode == Mode.DEADLINE ? TIMEOUT_MS : mode == Mode.QUEUE_TIME ? 50 : Long.MAX_VALUE;
                        if (waited > limit) {                         // ждал дольше допустимого — не работаем
                            rejected.incrementAndGet();
                            ex.sendResponseHeaders(503, -1);
                            return;
                        }
                        Thread.sleep(10);                                       // «работа»
                        if (waited + 10 > TIMEOUT_MS) wasted.incrementAndGet(); // ответ уже никому не нужен
                        else worked.incrementAndGet();
                        ex.sendResponseHeaders(200, -1);
                    } catch (Exception ignored) {
                    } finally { ex.close(); }
                });
            } catch (RejectedExecutionException full) {                       // очередь полна: быстрый отказ
                rejected.incrementAndGet();
                ex.sendResponseHeaders(503, -1);
                ex.close();
            }
        });
        server.start();

        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        AtomicInteger ok = new AtomicInteger(), sent = new AtomicInteger();
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).timeout(Duration.ofMillis(TIMEOUT_MS)).build();
        try (var clients = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.nanoTime();
            for (int i = 0; i < RATE * SECONDS; i++) {
                long due = start + i * 1_000_000_000L / RATE;                   // открытый поток: не ждём ответов
                while (System.nanoTime() < due) Thread.onSpinWait();
                clients.submit(() -> {
                    for (int attempt = 0; attempt <= retries; attempt++) {
                        sent.incrementAndGet();
                        try {
                            if (http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) { ok.incrementAndGet(); return; }
                            return;                                            // 503 — не повторяем
                        } catch (HttpTimeoutException timeout) { /* повтор, если разрешён */ }
                        catch (Exception e) { return; }
                    }
                });
            }
        }
        String name = switch (mode) { case UNBOUNDED -> retries > 0 ? "unbounded + 2 retries" : "unbounded queue"; case SHED -> "bounded queue, 503"; case DEADLINE -> "drop after 300 ms wait"; case QUEUE_TIME -> "drop after 50 ms wait"; };
        System.out.printf("   %-22s sent %5d  ok %5d (%3d/s)  server: useful %5d, late %5d, rejected %5d, queue left %d%n",
                name, sent.get(), ok.get(), ok.get() / SECONDS, worked.get(), wasted.get(), rejected.get(), queue.size());
        server.stop(0);
        pool.shutdownNow();
    }
}
