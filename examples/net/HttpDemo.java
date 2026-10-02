import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.CookieManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: java HttpDemo.java   (JDK 21, loopback)
public class HttpDemo {
    static final AtomicInteger paymentsCreated = new AtomicInteger();
    static final Map<String, String> byIdempotencyKey = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/old-pay-302", ex -> redirect(ex, 302, "/pay"));
        server.createContext("/old-pay-307", ex -> redirect(ex, 307, "/pay"));
        server.createContext("/pay", ex -> reply(ex, 200, "pay got " + ex.getRequestMethod()
                + " body=" + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        server.createContext("/tariff", HttpDemo::tariff);
        server.createContext("/payments", HttpDemo::payments);
        server.createContext("/login", ex -> {
            ex.getResponseHeaders().add("Set-Cookie", "SESSION=abc; Path=/; HttpOnly; SameSite=Lax");
            ex.getResponseHeaders().add("Set-Cookie", "TRACK=xyz; Path=/; Secure");
            reply(ex, 200, "logged in");
        });
        server.createContext("/me", ex -> reply(ex, 200, "cookies sent: " + ex.getRequestHeaders().getOrDefault("Cookie", List.of())));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new CookieManager())
                .build();

        System.out.println("== redirects");
        for (String path : List.of("/old-pay-302", "/old-pay-307")) {
            var r = client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"cents\":500}")).build(), HttpResponse.BodyHandlers.ofString());
            System.out.println("POST " + path + " -> " + r.statusCode() + " " + r.body());
        }

        System.out.println("== conditional GET");
        var first = client.send(HttpRequest.newBuilder(URI.create(base + "/tariff")).build(), HttpResponse.BodyHandlers.ofString());
        String etag = first.headers().firstValue("ETag").orElseThrow();
        System.out.println("GET /tariff -> " + first.statusCode() + ", ETag " + etag + ", body " + first.body().length() + " bytes");
        var again = client.send(HttpRequest.newBuilder(URI.create(base + "/tariff")).header("If-None-Match", etag).build(),
                HttpResponse.BodyHandlers.ofString());
        System.out.println("GET /tariff If-None-Match -> " + again.statusCode() + ", body " + again.body().length() + " bytes");

        System.out.println("== retrying POST");
        for (String key : new String[] {null, "7f3c-01"}) {
            paymentsCreated.set(0);
            for (int attempt = 1; attempt <= 2; attempt++) { // Второй раз — «повтор после таймаута».
                var b = HttpRequest.newBuilder(URI.create(base + "/payments"))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"cents\":500}"));
                if (key != null) b.header("Idempotency-Key", key);
                var r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                System.out.println((key == null ? "no key" : "key " + key) + ", attempt " + attempt + " -> " + r.statusCode() + " " + r.body());
            }
            System.out.println("payments created: " + paymentsCreated.get());
        }

        System.out.println("== cookies over plain http");
        client.send(HttpRequest.newBuilder(URI.create(base + "/login")).build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(client.send(HttpRequest.newBuilder(URI.create(base + "/me")).build(), HttpResponse.BodyHandlers.ofString()).body());
        server.stop(0);
    }

    static void tariff(HttpExchange ex) throws java.io.IOException {
        String etag = "\"tariff-v42\"";
        if (etag.equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
            ex.getResponseHeaders().add("ETag", etag);
            ex.sendResponseHeaders(304, -1); // Тело не передаётся.
            ex.close();
            return;
        }
        ex.getResponseHeaders().add("ETag", etag);
        ex.getResponseHeaders().add("Cache-Control", "max-age=60");
        reply(ex, 200, "{\"bank\":\"sber\",\"fee\":\"1.5%\",\"limits\":[1000,50000,250000]}");
    }

    static void payments(HttpExchange ex) throws java.io.IOException {
        String key = ex.getRequestHeaders().getFirst("Idempotency-Key");
        if (key != null) {
            String existing = byIdempotencyKey.get(key);
            if (existing != null) { reply(ex, 200, existing + " (replayed)"); return; }
        }
        String id = "p-" + (100 + paymentsCreated.incrementAndGet());
        if (key != null) byIdempotencyKey.put(key, id);
        reply(ex, 201, id);
    }

    static void redirect(HttpExchange ex, int code, String to) throws java.io.IOException {
        ex.getRequestBody().readAllBytes();
        ex.getResponseHeaders().add("Location", to);
        ex.sendResponseHeaders(code, -1);
        ex.close();
    }

    static void reply(HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (var out = ex.getResponseBody()) { out.write(b); }
    }
}
