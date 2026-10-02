import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: PostgreSQL 17 на localhost:55432 (docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine),
//         затем java -cp "$(cat target/classpath.txt)" IdempotencyDemo.java
public class IdempotencyDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";
    static final AtomicInteger bankCharges = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            c.createStatement().execute("""
                    drop table if exists idempotency_key, payment;
                    create table payment(id bigserial primary key, merchant text, amount bigint);
                    create table idempotency_key(
                        merchant text, key text, request_hash text not null,
                        status text not null,                    -- IN_PROGRESS или DONE
                        response_code int, response_body text,
                        created_at timestamptz default now(),
                        primary key (merchant, key));            -- ключ уникален в рамках клиента
                    """);
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/naive/payments", IdempotencyDemo::naive);
        server.createContext("/payments", IdempotencyDemo::idempotent);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient client = HttpClient.newHttpClient();
        String body = "{\"amount\": 500}";

        System.out.println("== without idempotency: client times out at 200 ms and retries");
        send(client, base + "/naive/payments", null, body, 200);
        send(client, base + "/naive/payments", null, body, 2000);
        System.out.println("   bank charges: " + bankCharges.getAndSet(0));

        System.out.println("== with Idempotency-Key: same retry");
        send(client, base + "/payments", "k-1", body, 200);
        Thread.sleep(400);
        send(client, base + "/payments", "k-1", body, 2000);
        System.out.println("   bank charges: " + bankCharges.getAndSet(0));

        System.out.println("== two concurrent requests with the same key");
        var a = CompletableFuture.supplyAsync(() -> send(client, base + "/payments", "k-2", body, 2000));
        var b = CompletableFuture.supplyAsync(() -> send(client, base + "/payments", "k-2", body, 2000));
        a.join(); b.join();
        System.out.println("   bank charges: " + bankCharges.getAndSet(0));

        System.out.println("== same key, different body");
        send(client, base + "/payments", "k-1", "{\"amount\": 900}", 2000);
        server.stop(0);
    }

    // Без защиты: каждый запрос — новое списание.
    static void naive(HttpExchange ex) throws java.io.IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String id = charge(body);
        reply(ex, 201, "{\"payment\":\"" + id + "\"}");
    }

    static void idempotent(HttpExchange ex) throws java.io.IOException {
        String key = ex.getRequestHeaders().getFirst("Idempotency-Key");
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String hash = sha256(body);
        try (Connection c = DriverManager.getConnection(URL)) {
            // 1. Занять ключ. Уникальный индекс решает гонку: вставит только один.
            var ins = c.prepareStatement("""
                    insert into idempotency_key(merchant, key, request_hash, status) values ('m-7', ?, ?, 'IN_PROGRESS')
                    on conflict do nothing""");
            ins.setString(1, key); ins.setString(2, hash);
            if (ins.executeUpdate() == 0) {
                // 2. Ключ уже есть: тот же запрос — вернуть сохранённый ответ; другой — ошибка.
                var sel = c.prepareStatement("select request_hash, status, response_code, response_body from idempotency_key where merchant = 'm-7' and key = ?");
                sel.setString(1, key);
                var rs = sel.executeQuery(); rs.next();
                if (!rs.getString(1).equals(hash)) { reply(ex, 422, "{\"title\":\"key reused with a different request\"}"); return; }
                if (rs.getString(2).equals("IN_PROGRESS")) { reply(ex, 409, "{\"title\":\"request with this key is in progress\"}"); return; }
                ex.getResponseHeaders().set("Idempotent-Replayed", "true");
                reply(ex, rs.getInt(3), rs.getString(4));
                return;
            }
            // 3. Выполнить и сохранить ответ.
            String id = charge(body);
            String response = "{\"payment\":\"" + id + "\"}";
            var upd = c.prepareStatement("update idempotency_key set status = 'DONE', response_code = 201, response_body = ? where merchant = 'm-7' and key = ?");
            upd.setString(1, response); upd.setString(2, key);
            upd.executeUpdate();
            reply(ex, 201, response);
        } catch (SQLException e) {
            reply(ex, 500, "{\"title\":\"" + e.getMessage() + "\"}");
        }
    }

    // «Банк»: списание идёт 500 мс — дольше таймаута клиента.
    static String charge(String body) {
        bankCharges.incrementAndGet();
        try (Connection c = DriverManager.getConnection(URL)) {
            Thread.sleep(500);
            var rs = c.createStatement().executeQuery("insert into payment(merchant, amount) values ('m-7', 500) returning id");
            rs.next();
            return "p-" + rs.getLong(1);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static String send(HttpClient client, String url, String key, String body, long timeoutMs) {
        var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs)).POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) req.header("Idempotency-Key", key);
        String line;
        try {
            var r = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            line = "-> " + r.statusCode() + (r.headers().firstValue("Idempotent-Replayed").isPresent() ? " (replayed) " : " ") + r.body();
        } catch (Exception e) {
            line = "-> " + e.getClass().getSimpleName();
        }
        String out = String.format("   POST %-16s key=%-4s %s", url.substring(url.indexOf("/", 8)), key, line);
        System.out.println(out);
        return out;
    }

    static void reply(HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (var out = ex.getResponseBody()) { out.write(b); }
    }

    static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
