package kb.cloud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Запуск: ./run.sh CloudDemo
// Два экземпляра «банка», платёжный сервис с Feign, балансировкой и circuit breaker.
public class CloudDemo {

    // ---------- банк: два экземпляра ----------
    @RestController
    static class BankController {
        static final Map<String, AtomicInteger> HITS = new HashMap<>();
        static final AtomicBoolean BANK2_BROKEN = new AtomicBoolean();
        private final String name;

        BankController(org.springframework.core.env.Environment env) { name = env.getProperty("bank.name"); }

        @GetMapping("/charge")
        String charge(@RequestParam String id) {
            HITS.computeIfAbsent(name, n -> new AtomicInteger()).incrementAndGet();
            if (name.equals("bank-2") && BANK2_BROKEN.get()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); // 503
            }
            return name + " charged " + id;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(BankController.class)
    static class BankApp {}

    // ---------- платёжный сервис ----------
    // name — логическое имя сервиса; адрес выберет LoadBalancer из discovery.
    @FeignClient(name = "bank", fallback = BankFallback.class)
    interface BankClient {
        @GetMapping("/charge")
        String charge(@RequestParam("id") String id);
    }

    @Component
    static class BankFallback implements BankClient {
        public String charge(String id) { return "fallback: queued " + id; } // Отложить платёж.
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableFeignClients(clients = BankClient.class)
    @Import(BankFallback.class)
    static class PaymentApp {}

    static ConfigurableApplicationContext start(Class<?> app, Map<String, Object> props) {
        var a = new SpringApplication(app);
        var all = new HashMap<String, Object>(Map.of("spring.main.banner-mode", "off", "logging.level.root", "off"));
        all.putAll(props);
        a.setDefaultProperties(all);
        return a.run();
    }

    static int port(ConfigurableApplicationContext c) {
        return ((WebServerApplicationContext) c).getWebServer().getPort();
    }

    public static void main(String[] args) throws Exception {
        try (var bank1 = start(BankApp.class, Map.of("server.port", "0", "bank.name", "bank-1"));
             var bank2 = start(BankApp.class, Map.of("server.port", "0", "bank.name", "bank-2"));
             var payments = start(PaymentApp.class, Map.ofEntries(
                     Map.entry("spring.main.web-application-type", "none"),
                     // Статический discovery вместо Eureka/Consul: два экземпляра сервиса bank.
                     Map.entry("spring.cloud.discovery.client.simple.instances.bank[0].uri", "http://localhost:" + port(bank1)),
                     Map.entry("spring.cloud.discovery.client.simple.instances.bank[1].uri", "http://localhost:" + port(bank2)),
                     Map.entry("spring.cloud.openfeign.circuitbreaker.enabled", "true"),
                     // Окно из 4 вызовов, открыть при 50% ошибок, через 1 с пробовать снова.
                     Map.entry("resilience4j.circuitbreaker.configs.default.sliding-window-size", "4"),
                     Map.entry("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls", "4"),
                     Map.entry("resilience4j.circuitbreaker.configs.default.failure-rate-threshold", "50"),
                     Map.entry("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state", "1s"),
                     Map.entry("resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state", "2")))) {

            var client = payments.getBean(BankClient.class);
            var registry = payments.getBean(CircuitBreakerRegistry.class);

            System.out.println("== both banks healthy");
            for (int i = 1; i <= 4; i++) System.out.println("p-" + i + " -> " + client.charge("p-" + i));

            System.out.println("== bank-2 returns 503");
            BankController.BANK2_BROKEN.set(true);
            BankController.HITS.clear();
            List<String> lines = new ArrayList<>();
            for (int i = 5; i <= 12; i++) {
                String r = client.charge("p-" + i);
                var cb = registry.getAllCircuitBreakers().iterator().next();
                lines.add("p-" + i + " -> " + r + "  [" + cb.getState() + "]");
            }
            lines.forEach(System.out::println);
            System.out.println("breaker name: " + registry.getAllCircuitBreakers().iterator().next().getName());
            System.out.println("bank hits while broken: " + new java.util.TreeMap<>(BankController.HITS));

            System.out.println("== after 1.1 s, bank-2 still broken");
            Thread.sleep(1100);
            BankController.HITS.clear();
            for (int i = 13; i <= 15; i++) {
                String r = client.charge("p-" + i);
                var cb = registry.getAllCircuitBreakers().iterator().next();
                System.out.println("p-" + i + " -> " + r + "  [" + cb.getState() + "]");
            }
        }
    }
}
