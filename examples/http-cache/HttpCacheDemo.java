import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: java HttpCacheDemo.java   (origin на :8090, nginx-кеш из nginx.conf на :8080 — см. README)
public class HttpCacheDemo {
    static final ConcurrentHashMap<String, AtomicInteger> originHits = new ConcurrentHashMap<>();
    static volatile int tariffVersion = 1;

    public static void main(String[] args) throws Exception {
        HttpServer origin = HttpServer.create(new InetSocketAddress(8090), 0);
        origin.createContext("/", HttpCacheDemo::handle);
        origin.start();
        HttpClient client = HttpClient.newHttpClient();
        String edge = "http://localhost:8080";

        System.out.println("== public, max-age=3");
        for (int i = 0; i < 2; i++) get(client, edge + "/tariffs", null);
        Thread.sleep(4500);
        get(client, edge + "/tariffs", null);                       // Истёк, но ETag тот же: 304 от origin.
        tariffVersion = 2;
        get(client, edge + "/tariffs", null);                       // Изменился, но ещё свежий в кеше.
        Thread.sleep(4500);                                          // Срок nginx считает в целых секундах.
        get(client, edge + "/tariffs", null);                       // Истёк и изменился.

        System.out.println("== private, max-age=60");
        get(client, edge + "/me", "anna");
        get(client, edge + "/me", "boris");

        System.out.println("== Vary: Accept-Language");
        getLang(client, edge + "/terms", "ru");
        getLang(client, edge + "/terms", "en");
        getLang(client, edge + "/terms", "ru");

        System.out.println("== stale-while-revalidate=30, max-age=1");
        get(client, edge + "/rates", null);
        Thread.sleep(1500);
        get(client, edge + "/rates", null);                          // Старое сразу, обновление в фоне.
        Thread.sleep(300);
        get(client, edge + "/rates", null);

        System.out.println("origin hits: " + new java.util.TreeMap<>(originHits));
        origin.stop(0);
    }

    static void handle(HttpExchange ex) throws java.io.IOException {
        String path = ex.getRequestURI().getPath();
        originHits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
        String body, cc, etag = null;
        switch (path) {
            case "/tariffs" -> { body = "tariff v" + tariffVersion; cc = "public, max-age=3"; etag = "\"t" + tariffVersion + "\""; }
            case "/me" -> { body = "profile of " + ex.getRequestHeaders().getFirst("X-User"); cc = "private, max-age=60"; }
            case "/terms" -> {
                body = "terms in " + ex.getRequestHeaders().getFirst("Accept-Language");
                cc = "public, max-age=60";
                ex.getResponseHeaders().set("Vary", "Accept-Language");
            }
            default -> { body = "rates " + System.nanoTime() % 1000; cc = "public, max-age=1, stale-while-revalidate=30"; }
        }
        ex.getResponseHeaders().set("Cache-Control", cc);
        if (etag != null) {
            ex.getResponseHeaders().set("ETag", etag);
            if (etag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
                ex.sendResponseHeaders(304, -1);                      // Не изменилось: тело не пересылаем.
                ex.close();
                return;
            }
        }
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, b.length);
        try (var out = ex.getResponseBody()) { out.write(b); }
    }

    static void get(HttpClient c, String url, String user) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(url));
        if (user != null) req.header("X-User", user);
        print(url, c.send(req.build(), HttpResponse.BodyHandlers.ofString()), user == null ? "" : " (user " + user + ")");
    }

    static void getLang(HttpClient c, String url, String lang) throws Exception {
        print(url, c.send(HttpRequest.newBuilder(URI.create(url)).header("Accept-Language", lang).build(),
                HttpResponse.BodyHandlers.ofString()), " (" + lang + ")");
    }

    static void print(String url, HttpResponse<String> r, String note) {
        System.out.printf("GET %-8s%-13s -> %d %-11s %s%n", url.substring(url.lastIndexOf('/')), note, r.statusCode(),
                r.headers().firstValue("X-Cache").orElse("-"), r.body());
    }
}
