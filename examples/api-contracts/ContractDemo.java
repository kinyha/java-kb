import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Запуск: ./run.sh ContractDemo   — пишет out/v1.json и out/v2.json из двух версий контроллера.
// Code-first: OpenAPI строит springdoc по аннотациям и типам Java.
public class ContractDemo {

    // ---------- v1 ----------
    enum Status { AUTHORIZED, CAPTURED }
    record CreatePayment(@Positive long amount, String currency) {}
    record Payment(String id, long amount, String currency, Status status) {}

    @RestController
    static class PaymentsV1 {
        @PostMapping("/payments")
        ResponseEntity<Payment> create(@RequestBody CreatePayment r) {
            return ResponseEntity.created(URI.create("/payments/p-1")).body(new Payment("p-1", r.amount(), r.currency(), Status.AUTHORIZED));
        }

        @GetMapping("/payments/{id}")
        Payment get(@PathVariable String id) { return new Payment(id, 500, "RUB", Status.CAPTURED); }

        @GetMapping("/payments")
        List<Payment> list(@RequestParam(required = false) String merchant) { return List.of(); }
    }

    // ---------- v2: «небольшой рефакторинг» ----------
    enum Status2 { AUTHORIZED, CAPTURED, REFUND_PENDING }        // Новое значение.
    record CreatePayment2(@Positive long amountMinor, String currency) {} // Переименовано поле.
    record Payment2(String id, long amountMinor, String currency, Status2 status) {}

    @RestController
    static class PaymentsV2 {
        @PostMapping("/payments")
        ResponseEntity<Payment2> create(@RequestBody CreatePayment2 r) {
            return ResponseEntity.created(URI.create("/payments/p-1")).body(new Payment2("p-1", r.amountMinor(), r.currency(), Status2.AUTHORIZED));
        }

        @GetMapping("/payments/{id}")
        Payment2 get(@PathVariable String id) { return new Payment2(id, 500, "RUB", Status2.CAPTURED); }

        @GetMapping("/payments")
        List<Payment2> list(@RequestParam String merchant) { return List.of(); } // Стал обязательным.
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {}

    static void dump(Class<?> controller, String file) throws Exception {
        var app = new SpringApplication(App.class, controller);
        app.setDefaultProperties(Map.of("server.port", "0", "spring.main.banner-mode", "off", "logging.level.root", "warn"));
        try (var ctx = app.run()) {
            int port = ((WebServerApplicationContext) ctx).getWebServer().getPort();
            String json = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs")).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            Files.createDirectories(Path.of("out"));
            Files.writeString(Path.of("out", file), json);
            System.out.println(file + ": " + json.length() + " bytes");
            if (file.equals("v1.json")) {
                var root = new tools.jackson.databind.ObjectMapper().readTree(json);
                System.out.println("  CreatePayment: " + root.at("/components/schemas/CreatePayment"));
                System.out.println("  POST /payments responses: " + root.at("/paths/~1payments/post/responses"));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        dump(PaymentsV1.class, "v1.json");
        dump(PaymentsV2.class, "v2.json");
    }
}
