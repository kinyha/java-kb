import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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

// Запуск: java RestDemo.java   (JDK 21, loopback)
// Платёжный API: ресурсы, коды ответов, ссылки на действия, долгая операция.
public class RestDemo {
    enum Status { AUTHORIZED, CAPTURED, CANCELLED }

    static final Map<String, Status> payments = new ConcurrentHashMap<>();
    static final Map<String, Integer> operationPolls = new ConcurrentHashMap<>();
    static final Map<String, String> operationTarget = new ConcurrentHashMap<>();
    static final AtomicInteger ids = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/payments", RestDemo::payments);
        server.createContext("/operations", RestDemo::operations);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var client = HttpClient.newHttpClient();

        show(client, "POST", base + "/payments", "{\"amount\":500}");
        show(client, "GET", base + "/payments/p-1", null);
        String op = show(client, "POST", base + "/payments/p-1/captures", "{}");
        show(client, "GET", base + op, null);   // Операция ещё идёт.
        show(client, "GET", base + op, null);   // Готово: ссылка на результат.
        show(client, "GET", base + "/payments/p-1", null);
        show(client, "POST", base + "/payments/p-1/cancellations", "{}");
        show(client, "DELETE", base + "/payments/p-1", null);
        show(client, "GET", base + "/payments/p-404", null);
        server.stop(0);
    }

    // Печатает запрос и ответ; возвращает Location, если он есть.
    static String show(HttpClient client, String method, String url, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url)).method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        String path = URI.create(url).getPath();
        StringBuilder line = new StringBuilder(method + " " + path + " -> " + r.statusCode());
        for (String h : List.of("Location", "Retry-After", "Allow")) {
            r.headers().firstValue(h).ifPresent(v -> line.append(" ").append(h).append(": ").append(v));
        }
        if (!r.body().isEmpty()) line.append("\n    ").append(r.body());
        System.out.println(line);
        return r.headers().firstValue("Location").orElse(null);
    }

    static void payments(HttpExchange ex) throws java.io.IOException {
        String[] parts = ex.getRequestURI().getPath().split("/"); // ["", "payments", id, sub]
        String method = ex.getRequestMethod();
        if (parts.length == 2) {                                  // /payments
            if (!method.equals("POST")) { notAllowed(ex, "POST"); return; }
            String id = "p-" + ids.incrementAndGet();
            payments.put(id, Status.AUTHORIZED);
            ex.getResponseHeaders().add("Location", "/payments/" + id);
            send(ex, 201, view(id));
            return;
        }
        String id = parts[2];
        Status s = payments.get(id);
        if (s == null) { send(ex, 404, problem("payment not found", id)); return; }
        if (parts.length == 3) {                                  // /payments/{id}
            if (!method.equals("GET")) { notAllowed(ex, "GET"); return; }
            send(ex, 200, view(id));
            return;
        }
        String action = parts[3];                                 // /payments/{id}/captures|cancellations
        if (!method.equals("POST")) { notAllowed(ex, "POST"); return; }
        if (s != Status.AUTHORIZED) {
            send(ex, 409, problem("payment is " + s + ", " + action + " not allowed", id));
            return;
        }
        if (action.equals("captures")) {                          // Банк отвечает не сразу.
            String op = "op-" + ids.incrementAndGet();
            operationPolls.put(op, 0);
            operationTarget.put(op, id);
            ex.getResponseHeaders().add("Location", "/operations/" + op);
            send(ex, 202, "{\"operation\":\"" + op + "\",\"status\":\"RUNNING\"}");
        } else {
            payments.put(id, Status.CANCELLED);
            send(ex, 200, view(id));
        }
    }

    static void operations(HttpExchange ex) throws java.io.IOException {
        String op = ex.getRequestURI().getPath().substring("/operations/".length());
        int polls = operationPolls.merge(op, 1, Integer::sum);
        if (polls == 1) {                                         // Первый опрос: ещё идёт.
            ex.getResponseHeaders().add("Retry-After", "1");
            send(ex, 200, "{\"operation\":\"" + op + "\",\"status\":\"RUNNING\"}");
            return;
        }
        String id = operationTarget.get(op);
        payments.put(id, Status.CAPTURED);
        send(ex, 200, "{\"operation\":\"" + op + "\",\"status\":\"DONE\",\"links\":{\"payment\":\"/payments/" + id + "\"}}");
    }

    // Представление платежа: ссылки только на действия, доступные в текущем состоянии.
    static String view(String id) {
        Status s = payments.get(id);
        String links = switch (s) {
            case AUTHORIZED -> "\"self\":\"/payments/" + id + "\",\"capture\":\"/payments/" + id + "/captures\","
                    + "\"cancel\":\"/payments/" + id + "/cancellations\"";
            case CAPTURED -> "\"self\":\"/payments/" + id + "\",\"refund\":\"/payments/" + id + "/refunds\"";
            case CANCELLED -> "\"self\":\"/payments/" + id + "\"";
        };
        return "{\"id\":\"" + id + "\",\"status\":\"" + s + "\",\"links\":{" + links + "}}";
    }

    static String problem(String detail, String id) {
        return "{\"title\":\"" + detail + "\",\"instance\":\"/payments/" + id + "\"}";
    }

    static void notAllowed(HttpExchange ex, String allow) throws java.io.IOException {
        ex.getResponseHeaders().add("Allow", allow);
        send(ex, 405, "");
    }

    static void send(HttpExchange ex, int code, String body) throws java.io.IOException {
        ex.getRequestBody().readAllBytes();
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        if (code == 409 || code == 404) ex.getResponseHeaders().add("Content-Type", "application/problem+json");
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) try (var out = ex.getResponseBody()) { out.write(b); }
        ex.close();
    }
}
