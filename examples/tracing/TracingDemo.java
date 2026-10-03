import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.Executors;

// Запуск: Jaeger 2 в Docker (см. run.sh), затем java -cp "$(cat target/classpath.txt)" TracingDemo.java
// gateway -> orders -> payments; orders читает базу 30 мс, payments ждёт провайдера 250 мс.
public class TracingDemo {
    static final HttpClient http = HttpClient.newHttpClient();
    static final W3CTraceContextPropagator W3C = W3CTraceContextPropagator.getInstance();
    static final List<SdkTracerProvider> providers = new ArrayList<>();
    static volatile boolean ordersPropagates = true;

    static final TextMapGetter<HttpExchange> GETTER = new TextMapGetter<>() {
        public Iterable<String> keys(HttpExchange ex) { return ex.getRequestHeaders().keySet(); }
        public String get(HttpExchange ex, String key) { return ex.getRequestHeaders().getFirst(key); }
    };

    static Tracer tracer(String service, Sampler sampler) {
        SdkTracerProvider p = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), service))))
                .setSampler(sampler)
                .addSpanProcessor(BatchSpanProcessor.builder(OtlpHttpSpanExporter.builder().setEndpoint("http://localhost:4318/v1/traces").build()).build())
                .build();
        providers.add(p);
        return p.get("demo");
    }

    interface Work { void run(String path) throws Exception; }

    // Сервер: достать контекст из traceparent, открыть серверный span, выполнить работу.
    static void serve(int port, Tracer t, Work w) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 100);
        s.setExecutor(Executors.newFixedThreadPool(16));
        s.createContext("/", ex -> {
            Context parent = W3C.extract(Context.root(), ex, GETTER);
            String path = ex.getRequestURI().getPath();
            Span span = t.spanBuilder("GET " + path).setParent(parent).setSpanKind(SpanKind.SERVER).startSpan();
            try (Scope ignored = span.makeCurrent()) { w.run(path); }
            catch (Exception e) { span.recordException(e); span.setStatus(StatusCode.ERROR); }
            finally { span.end(); ex.sendResponseHeaders(200, -1); ex.close(); }
        });
        s.start();
    }

    // Клиент: клиентский span и traceparent в заголовке исходящего запроса.
    static void call(Tracer t, int port, String path, boolean inject) throws Exception {
        Span span = t.spanBuilder("call " + path).setSpanKind(SpanKind.CLIENT).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (inject) W3C.inject(Context.current(), b, (req, k, v) -> req.header(k, v));
            http.send(b.build(), HttpResponse.BodyHandlers.discarding());
        } finally { span.end(); }
    }

    static void local(Tracer t, String name, long ms) throws InterruptedException {
        Span span = t.spanBuilder(name).startSpan();
        try (Scope ignored = span.makeCurrent()) { Thread.sleep(ms); } finally { span.end(); }
    }

    public static void main(String[] args) throws Exception {
        // Решение о выборке принимает первый сервис; остальные следуют решению родителя.
        Tracer gw = tracer("gateway", Sampler.parentBased(Sampler.traceIdRatioBased(0.1)));
        Tracer orders = tracer("orders", Sampler.parentBased(Sampler.alwaysOn()));
        Tracer payments = tracer("payments", Sampler.parentBased(Sampler.alwaysOn()));
        Tracer gwAll = tracer("gateway-full", Sampler.alwaysOn());

        serve(8501, gwAll, p -> call(gwAll, 8502, p, true));
        serve(8511, gw, p -> call(gw, 8502, p, true));
        serve(8502, orders, p -> { local(orders, "SELECT orders", 30); call(orders, 8503, p, ordersPropagates); });
        serve(8503, payments, p -> local(payments, "psp.charge", 250));

        http.send(HttpRequest.newBuilder(URI.create("http://localhost:8501/checkout")).build(), HttpResponse.BodyHandlers.discarding());
        ordersPropagates = false;
        http.send(HttpRequest.newBuilder(URI.create("http://localhost:8501/broken")).build(), HttpResponse.BodyHandlers.discarding());
        ordersPropagates = true;
        for (int i = 0; i < 200; i++)
            http.send(HttpRequest.newBuilder(URI.create("http://localhost:8511/sampled")).build(), HttpResponse.BodyHandlers.discarding());
        for (var p : providers) p.forceFlush().join(10, java.util.concurrent.TimeUnit.SECONDS);
        Thread.sleep(3000);

        System.out.println("== one checkout request, spans from three services");
        for (JsonNode tr : traces("gateway-full", "GET /checkout")) printTree(tr);
        System.out.println("== orders forgot to inject traceparent into the call to payments");
        List<JsonNode> broken = new ArrayList<>(traces("gateway-full", "GET /broken"));
        broken.addAll(traces("payments", "GET /broken"));
        for (JsonNode tr : broken) printTree(tr);
        System.out.println("== 200 requests, gateway samples 10%, others follow the parent");
        List<JsonNode> sampled = traces("gateway", "GET /sampled");
        long complete = sampled.stream().filter(tr -> tr.path("spans").size() == 7).count();
        System.out.println("   traces stored: " + sampled.size() + ", complete with all 7 spans: " + complete);
        System.out.println("   payments spans without their gateway trace: " + (traces("payments", "GET /sampled").size() - sampled.size()));
        System.exit(0);
    }

    static List<JsonNode> traces(String service, String op) throws Exception {
        String url = "http://localhost:16686/api/traces?service=" + service + "&operation=" + op.replace(" ", "%20") + "&limit=1000&lookback=1h";
        JsonNode root = new ObjectMapper().readTree(http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).body());
        List<JsonNode> out = new ArrayList<>();
        root.path("data").forEach(out::add);
        return out;
    }

    static void printTree(JsonNode trace) {
        Map<String, JsonNode> byId = new HashMap<>();
        Map<String, List<JsonNode>> children = new HashMap<>();
        trace.path("spans").forEach(s -> byId.put(s.path("spanID").asText(), s));
        List<JsonNode> roots = new ArrayList<>();
        trace.path("spans").forEach(s -> {
            String parent = null;
            for (JsonNode r : s.path("references")) if (r.path("refType").asText().equals("CHILD_OF")) parent = r.path("spanID").asText();
            if (parent == null || !byId.containsKey(parent)) roots.add(s); else children.computeIfAbsent(parent, k -> new ArrayList<>()).add(s);
        });
        System.out.println("   trace " + trace.path("traceID").asText().substring(0, 8) + "…, spans: " + trace.path("spans").size());
        for (JsonNode r : roots) print(trace, r, children, 1);
    }

    static void print(JsonNode trace, JsonNode s, Map<String, List<JsonNode>> children, int depth) {
        String svc = trace.path("processes").path(s.path("processID").asText()).path("serviceName").asText();
        String name = "  ".repeat(depth) + s.path("operationName").asText();
        System.out.printf("   %-30s %-12s %4d ms%n", name, svc, s.path("duration").asLong() / 1000);
        List<JsonNode> kids = new ArrayList<>(children.getOrDefault(s.path("spanID").asText(), List.of()));
        kids.sort(Comparator.comparingLong(k -> k.path("startTime").asLong()));
        for (JsonNode k : kids) print(trace, k, children, depth + 1);
    }
}
