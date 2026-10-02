package kb.actuator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Запуск: ./run.sh ActuatorDemo
// Первый контекст — настройки по умолчанию, второй — открытые endpoints и probes.
public class ActuatorDemo {

    // Локальная замена банка: флаг переключает доступность.
    @Component
    static class Bank {
        final AtomicBoolean up = new AtomicBoolean(true);
    }

    // Имя бина без суффикса HealthIndicator становится именем компонента: "bank".
    @Component("bankHealthIndicator")
    static class BankHealthIndicator implements HealthIndicator {
        private final Bank bank;
        BankHealthIndicator(Bank bank) { this.bank = bank; }

        @Override
        public Health health() {
            return bank.up.get()
                    ? Health.up().withDetail("latencyMs", 42).build()
                    : Health.down().withDetail("error", "connect timed out").build();
        }
    }

    @RestController
    static class PaymentController {
        private final Bank bank;
        private final MeterRegistry registry;
        PaymentController(Bank bank, MeterRegistry registry) { this.bank = bank; this.registry = registry; }

        @PostMapping("/pay")
        String pay(@RequestParam String id) {
            Timer.Sample sample = Timer.start(registry);
            String outcome = bank.up.get() ? "ok" : "bank_down";
            // Тег с малым числом значений; id платежа в тег не кладём.
            sample.stop(registry.timer("payments.charge", "outcome", outcome));
            return outcome + ":" + id;
        }
    }

    @SpringBootApplication
    static class App {}

    static final HttpClient http = HttpClient.newHttpClient();
    static String base;

    static String call(String method, String path) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(base + path)).method(method, HttpRequest.BodyPublishers.noBody()).build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() + " " + res.body();
    }

    // Вырезает из JSON health один компонент: "name":{...}.
    static String pick(String json, String name) {
        var m = java.util.regex.Pattern.compile("\"" + name + "\":\\{(\"details\":\\{[^}]*\\},)?\"status\":\"\\w+\"\\}").matcher(json);
        return m.find() ? m.group() : "";
    }

    static ConfigurableApplicationContext start(Map<String, Object> props) {
        var app = new SpringApplication(App.class);
        app.setDefaultProperties(merge(props));
        var ctx = app.run();
        base = "http://localhost:" + ((WebServerApplicationContext) ctx).getWebServer().getPort();
        return ctx;
    }

    static Map<String, Object> merge(Map<String, Object> props) {
        var all = new java.util.HashMap<String, Object>(Map.of("server.port", "0",
                "spring.main.banner-mode", "off", "logging.level.root", "warn"));
        all.putAll(props);
        return all;
    }

    public static void main(String[] args) throws Exception {
        try (var ctx = start(Map.of())) {
            System.out.println("== defaults");
            System.out.println("GET /actuator -> " + call("GET", "/actuator").replaceAll("\"href\":\"[^\"]*\",?", "").replaceAll("\"templated\":(true|false)", ""));
            System.out.println("GET /actuator/health -> " + call("GET", "/actuator/health"));
            System.out.println("GET /actuator/metrics -> " + call("GET", "/actuator/metrics").split(" ")[0]);
        }

        try (var ctx = start(Map.of(
                "management.endpoints.web.exposure.include", "health,metrics,prometheus",
                "management.endpoint.health.show-details", "always"))) {
            var bank = ctx.getBean(Bank.class);

            System.out.println("== bank up");
            System.out.println("health    -> " + call("GET", "/actuator/health/bank"));
            System.out.println("pay       -> " + call("POST", "/pay?id=p-1"));

            System.out.println("== bank down");
            bank.up.set(false);
            System.out.println("pay       -> " + call("POST", "/pay?id=p-2"));
            System.out.println("pay       -> " + call("POST", "/pay?id=p-3"));
            String health = call("GET", "/actuator/health");
            System.out.println("health    -> " + health.substring(0, 4) + pick(health, "bank") + " ... " + pick(health, "readinessState")
                    + " ... status=" + health.replaceAll(".*\"status\":\"(\\w+)\"}$", "$1"));
            System.out.println("readiness -> " + call("GET", "/actuator/health/readiness"));
            System.out.println("liveness  -> " + call("GET", "/actuator/health/liveness"));

            System.out.println("== app refuses traffic");
            AvailabilityChangeEvent.publish(ctx, ReadinessState.REFUSING_TRAFFIC);
            System.out.println("readiness -> " + call("GET", "/actuator/health/readiness"));
            System.out.println("liveness  -> " + call("GET", "/actuator/health/liveness"));

            System.out.println("== metrics");
            String m = call("GET", "/actuator/metrics/payments.charge?tag=outcome:bank_down");
            System.out.println("payments.charge outcome=bank_down -> " + m.replaceAll(".*\"statistic\":\"COUNT\",\"value\":([0-9.]+).*", "COUNT=$1"));
            for (String line : call("GET", "/actuator/prometheus").split("\n")) {
                if (line.startsWith("payments_charge_seconds_count")) System.out.println(line);
            }
        }
    }
}
