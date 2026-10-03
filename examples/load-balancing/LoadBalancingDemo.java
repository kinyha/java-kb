import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: nginx в Docker с nginx.conf (см. README), затем java LoadBalancingDemo.java
// Бэкенды — HttpServer на хосте, у каждого 4 рабочих потока. 8093 отвечает за 200 мс, остальные — за 20 мс.
public class LoadBalancingDemo {
    static final Map<Integer, AtomicInteger> served = new ConcurrentSkipListMap<>();
    static final HttpClient http = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        backend(8091, 20); backend(8092, 20); backend(8093, 200);
        crashingBackend(8094);

        System.out.println("== 24 clients, 1200 requests; 8093 is 10x slower");
        load("round robin", 8081);
        load("least_conn", 8082);

        System.out.println("== backend 8094 dies in the middle of a request, 8091 is the backup");
        crash("GET ", 8083);
        crash("POST", 8083);
        crash("POST", 8084);
        System.exit(0);
    }

    static void backend(int port, int delayMs) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 100);
        s.setExecutor(Executors.newFixedThreadPool(4));             // ёмкость: 4 запроса одновременно
        s.createContext("/", ex -> {
            served.computeIfAbsent(port, p -> new AtomicInteger()).incrementAndGet();
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            byte[] body = String.valueOf(port).getBytes();
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        s.start();
    }

    // Принял запрос, выполнил «побочный эффект» и упал, не ответив.
    static void crashingBackend(int port) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 100);
        s.createContext("/", ex -> {
            served.computeIfAbsent(port, p -> new AtomicInteger()).incrementAndGet();
            ex.getRequestBody().readAllBytes();
            ex.close();                                            // закрыть без ответа: nginx видит обрыв
        });
        s.start();
    }

    static void crash(String method, int port) throws Exception {
        served.clear();
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/pay"))
                .method(method.trim(), HttpRequest.BodyPublishers.ofString("{}")).build();
        int code = http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
        System.out.printf("   %s via :%d -> %d, executed on %s%n", method, port, code, served);
    }

    static void load(String name, int port) throws Exception {
        served.clear();
        int clients = 24, perClient = 50;
        long[] lat = new long[clients * perClient];
        AtomicInteger idx = new AtomicInteger();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int c = 0; c < clients; c++)
                pool.submit(() -> {
                    for (int i = 0; i < perClient; i++) {
                        long t = System.nanoTime();
                        http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(),
                                HttpResponse.BodyHandlers.discarding());
                        lat[idx.getAndIncrement()] = (System.nanoTime() - t) / 1_000_000;
                    }
                    return null;
                });
        }
        Arrays.sort(lat);
        System.out.printf("   %-12s served %s   p50 %3d ms, p99 %3d ms%n", name, served,
                lat[lat.length / 2], lat[(int) (lat.length * 0.99)]);
    }
}
