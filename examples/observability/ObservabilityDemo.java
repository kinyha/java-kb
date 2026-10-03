import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

// Запуск: java -Dlogback.configurationFile=logback.xml -cp "$(cat target/classpath.txt)" ObservabilityDemo.java
// Три сервиса в одном процессе: gateway -> orders -> payments. Каждый 50-й платёж медленный, запрос 137 падает.
public class ObservabilityDemo {
    static final HttpClient http = HttpClient.newHttpClient();
    static final Executor analytics = Executors.newFixedThreadPool(2);
    static volatile boolean propagate;
    static volatile boolean copyMdc;

    public static void main(String[] args) throws Exception {
        service("gateway", 8301, (n, ex) -> call(8302, n));
        service("orders", 8302, (n, ex) -> {
            Runnable sendEvent = () -> LoggerFactory.getLogger("orders").info("analytics event sent");
            CompletableFuture.runAsync(copyMdc ? withMdc(sendEvent) : sendEvent, analytics);   // работа в другом потоке
            return call(8303, n);
        });
        service("payments", 8303, (n, ex) -> {
            if (n % 50 == 0) Thread.sleep(400);
            if (n == 137) throw new IllegalStateException("card processor returned 91: issuer unavailable");
            Thread.sleep(20);
            return 200;
        });

        run(false, false);
        System.out.println("== without request id propagation");
        report();
        run(true, true);
        System.out.println("== with X-Request-Id propagated and copied to the async thread");
        report();
        goldenSignals();
        System.exit(0);
    }

    interface Handler { int handle(int n, HttpExchange ex) throws Exception; }

    static void service(String name, int port, Handler h) throws Exception {
        Logger log = LoggerFactory.getLogger(name);
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 100);
        s.setExecutor(Executors.newFixedThreadPool(8));
        s.createContext("/", ex -> {
            String incoming = ex.getRequestHeaders().getFirst("X-Request-Id");
            // Без передачи каждый сервис придумывает свой идентификатор.
            MDC.put("requestId", propagate && incoming != null ? incoming : UUID.randomUUID().toString().substring(0, 8));
            int n = Integer.parseInt(ex.getRequestURI().getPath().substring(1));
            long t = System.nanoTime();
            int code;
            try { code = h.handle(n, ex); }
            catch (Exception e) { log.error("request failed", e); code = 500; }
            log.info("{} /{} -> {} in {} ms", ex.getRequestMethod(), n, code, (System.nanoTime() - t) / 1_000_000);
            ex.sendResponseHeaders(code, -1);
            ex.close();
            MDC.clear();
        });
        s.start();
    }

    static int call(int port, int n) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + n));
        if (propagate) b.header("X-Request-Id", MDC.get("requestId"));
        return http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // MDC живёт в ThreadLocal: в другой поток его надо перенести вручную.
    static Runnable withMdc(Runnable r) {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> { MDC.setContextMap(ctx); try { r.run(); } finally { MDC.clear(); } };
    }

    static void run(boolean prop, boolean copy) throws Exception {
        propagate = prop; copyMdc = copy;
        Files.deleteIfExists(Path.of("logs/app.json"));
        ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).reset();
        new ch.qos.logback.classic.util.ContextInitializer((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).autoConfig();
        for (int i = 1; i <= 200; i++) http.send(HttpRequest.newBuilder(URI.create("http://localhost:8301/" + i)).build(), HttpResponse.BodyHandlers.discarding());
        Thread.sleep(300);
    }

    static List<JsonNode> logs() throws Exception {
        ObjectMapper m = new ObjectMapper();
        List<JsonNode> out = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("logs/app.json"))) out.add(m.readTree(line));
        return out;
    }

    static void report() throws Exception {
        List<JsonNode> all = logs();
        JsonNode error = all.stream().filter(j -> j.path("level").asText().equals("ERROR")).findFirst().orElseThrow();
        String id = error.path("requestId").asText();
        System.out.println("   lines in the shared log: " + all.size());
        System.out.println("   error: [" + error.path("logger_name").asText() + "] " + error.path("stack_trace").asText().lines().findFirst().orElse(""));
        System.out.println("   lines with its requestId " + id.substring(0, Math.min(8, id.length())) + ":");
        all.stream().filter(j -> j.path("requestId").asText().equals(id))
           .forEach(j -> System.out.printf("     %-8s %s%n", j.path("logger_name").asText(), j.path("message").asText()));
        long asyncWithout = all.stream().filter(j -> j.path("message").asText().equals("analytics event sent") && j.path("requestId").isMissingNode()).count();
        System.out.println("   async 'analytics event sent' lines without requestId: " + asyncWithout + " of 200");
    }

    static void goldenSignals() throws Exception {
        List<Long> ms = new ArrayList<>();
        int errors = 0;
        for (JsonNode j : logs()) {
            if (!j.path("logger_name").asText().equals("gateway") || !j.path("message").asText().startsWith("GET")) continue;
            String msg = j.path("message").asText();
            if (msg.contains("-> 500")) errors++;
            ms.add(Long.parseLong(msg.replaceAll(".* in (\\d+) ms", "$1")));
        }
        Collections.sort(ms);
        double avg = ms.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.println("== golden signals from gateway logs");
        System.out.printf("   traffic %d requests, errors %d (%.1f%%)%n", ms.size(), errors, errors * 100.0 / ms.size());
        System.out.printf("   latency avg %.0f ms, p50 %d ms, p95 %d ms, p99 %d ms, max %d ms%n",
                avg, ms.get(ms.size() / 2), ms.get((int) (ms.size() * 0.95)), ms.get((int) (ms.size() * 0.99)), ms.get(ms.size() - 1));
    }
}
