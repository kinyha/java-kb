import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Запуск: Prometheus с prometheus.yml (см. run.sh), затем java -cp "$(cat target/classpath.txt)" MetricsDemo.java
// Два экземпляра сервиса платежей, каждый со своим реестром Micrometer и /metrics.
// A: 245 запросов в секунду по ~10 мс. B: 5 в секунду по ~300 мс. B перезапускается на 20-й секунде.
public class MetricsDemo {
    static volatile PrometheusMeterRegistry regA, regB;

    public static void main(String[] args) throws Exception {
        regA = registry(); regB = registry();
        expose(8401, () -> regA); expose(8402, () -> regB);
        // Метка с идентификатором пользователя — по ряду на каждого пользователя.
        for (int u = 0; u < 1000; u++) Counter.builder("orders").tag("user_id", "u" + u).register(regA).increment();

        Random rnd = new Random(1);
        long start = System.currentTimeMillis();
        for (int tick = 0; tick < 400; tick++) {                       // 40 секунд по 100 мс
            if (tick == 200) regB = registry();                         // перезапуск экземпляра B: счётчики с нуля
            for (int i = 0; i < 25; i++) timer(regA).record(Duration.ofMillis(8 + rnd.nextInt(5)));
            if (tick % 2 == 0) timer(regB).record(Duration.ofMillis(290 + rnd.nextInt(20)));
            long due = start + (tick + 1) * 100L;
            Thread.sleep(Math.max(0, due - System.currentTimeMillis()));
        }
        Thread.sleep(2500);                                            // последний опрос Prometheus

        System.out.println("== PromQL over the last 20 s");
        q("requests/s by instance", "sum by (instance) (rate(http_server_requests_seconds_count[20s]))");
        q("p99 from merged histograms", "histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket[20s])))");
        q("p99 of each instance", "histogram_quantile(0.99, sum by (instance, le) (rate(http_server_requests_seconds_bucket[20s])))");
        q("avg of per-instance p99", "avg(histogram_quantile(0.99, sum by (instance, le) (rate(http_server_requests_seconds_bucket[20s]))))");
        System.out.println("== counter reset when B restarted");
        q("raw counter of B now", "http_server_requests_seconds_count{instance=\"b\"}");
        q("resets of B in 45 s", "resets(http_server_requests_seconds_count{instance=\"b\"}[45s])");
        q("increase of B over 40 s", "increase(http_server_requests_seconds_count{instance=\"b\"}[40s])");
        System.out.println("== series count");
        q("orders{user_id=...} series", "count(orders_total)");
        q("buckets of one timer", "count(http_server_requests_seconds_bucket{instance=\"a\"})");
        System.exit(0);
    }

    static PrometheusMeterRegistry registry() { return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT); }

    static Timer timer(PrometheusMeterRegistry r) {
        return Timer.builder("http.server.requests").tag("uri", "/payments")
                .publishPercentileHistogram()                           // бакеты для histogram_quantile
                .register(r);
    }

    interface Reg { PrometheusMeterRegistry get(); }

    static void expose(int port, Reg reg) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 10);
        s.createContext("/metrics", ex -> {
            byte[] b = reg.get().scrape().getBytes(StandardCharsets.UTF_8);
            // Prometheus 3 отвергает ответ без Content-Type: «non-compliant scrape target».
            ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        s.start();
    }

    static void q(String title, String promql) throws Exception {
        String url = "http://localhost:9090/api/v1/query?query=" + URLEncoder.encode(promql, StandardCharsets.UTF_8);
        String body = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).body();
        var out = new java.util.TreeMap<String, String>();                 // экземпляры по алфавиту
        Matcher m = Pattern.compile("\"metric\":\\{([^}]*)\\},\"value\":\\[[^,]+,\"([^\"]+)\"\\]").matcher(body);
        while (m.find()) {
            String inst = m.group(1).replaceAll(".*\"instance\":\"(\\w+)\".*", "$1");
            double v = Double.parseDouble(m.group(2));
            out.put(m.group(1).contains("instance") ? inst : "", (m.group(1).contains("instance") ? inst + "=" : "")
                    + (v < 10 && v != Math.rint(v) ? String.format("%.3f", v) : String.format("%.0f", v)));
        }
        System.out.printf("   %-28s %s%n", title, String.join("  ", out.values()));
    }
}
