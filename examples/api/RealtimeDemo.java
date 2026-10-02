import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Запуск: java RealtimeDemo.java   (JDK 21, loopback)
public class RealtimeDemo {
    // Журнал событий платежа: id события = позиция в журнале.
    static final List<String> events = new CopyOnWriteArrayList<>();
    static final Object changed = new Object();

    static void publish(String status) {
        synchronized (changed) { events.add(status); changed.notifyAll(); }
    }

    // Ждёт события с номером > after не дольше timeoutMs. Возвращает номер последнего события.
    static int awaitAfter(int after, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        synchronized (changed) {
            while (events.size() <= after) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                changed.wait(left);
            }
            return events.size();
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Long polling: ответ, как только появилось событие новее ?after, или 204 по таймауту.
        server.createContext("/poll", ex -> {
            int after = Integer.parseInt(ex.getRequestURI().getQuery().split("=")[1]);
            try {
                int last = awaitAfter(after, 500);
                if (last == after) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
                send(ex, String.join(",", events.subList(after, last)) + " (next after=" + last + ")");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // SSE: один долгий ответ text/event-stream; продолжает с Last-Event-ID.
        server.createContext("/stream", ex -> {
            String lastId = ex.getRequestHeaders().getFirst("Last-Event-ID");
            int sent = lastId == null ? 0 : Integer.parseInt(lastId);
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);                         // 0 = chunked, длина неизвестна.
            try (OutputStream out = ex.getResponseBody()) {
                out.write("retry: 1000\n\n".getBytes());
                out.flush();
                while (sent < 4) {
                    int last = awaitAfter(sent, 2000);
                    for (; sent < last; sent++) {
                        out.write(("id: " + (sent + 1) + "\nevent: status\ndata: " + events.get(sent) + "\n\n").getBytes());
                        out.flush();                                 // Без flush событие застрянет в буфере.
                    }
                }
            } catch (Exception e) {
                System.out.println("server: stream closed by client after event " + sent);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient client = HttpClient.newHttpClient();

        System.out.println("== long polling");
        publish("AUTHORIZED");
        Thread.ofVirtual().start(() -> { sleep(200); publish("CAPTURED"); });
        for (int after : new int[] {0, 1, 2}) {
            long t = System.nanoTime();
            var r = client.send(HttpRequest.newBuilder(URI.create(base + "/poll?after=" + after)).build(), HttpResponse.BodyHandlers.ofString());
            System.out.printf("GET /poll?after=%d -> %d after ~%d ms %s%n", after, r.statusCode(),
                    Math.round((System.nanoTime() - t) / 1e8) * 100, r.body());
        }

        System.out.println("== server-sent events");
        Thread.ofVirtual().start(() -> { sleep(150); publish("SETTLED"); sleep(150); publish("REFUND_PENDING"); });
        String lastSeen = readStream(client, base, null, 3);          // Обрываем после третьего события.
        System.out.println("-- reconnect with Last-Event-ID: " + lastSeen);
        readStream(client, base, lastSeen, 99);

        server.stop(0);
    }

    // Читает поток SSE построчно; после maxEvents событий закрывает соединение. Возвращает последний id.
    static String readStream(HttpClient client, String base, String lastId, int maxEvents) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(base + "/stream"));
        if (lastId != null) req.header("Last-Event-ID", lastId);
        var resp = client.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
        String id = lastId;
        int seen = 0;
        try (var in = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                if (line.startsWith("id: ")) id = line.substring(4);
                if (line.startsWith("data: ")) {
                    System.out.println("event id=" + id + " " + line);
                    if (++seen == maxEvents) break;
                }
            }
        }
        return id;
    }

    static void send(HttpExchange ex, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(b); }
    }

    static void sleep(long ms) {
        try { TimeUnit.MILLISECONDS.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
