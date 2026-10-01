import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.registry.ImportHttpServices;

// RestClient без таймаутов и с настройками Boot, ошибки 4xx, HTTP-интерфейс.
public class ClientDemo {
    record Payment(long id, String status) {}

    static class PaymentNotFoundException extends RuntimeException {
        PaymentNotFoundException(long id) { super("payment " + id + " not found at bank"); }
    }

    // Декларативный клиент: Spring сгенерирует реализацию поверх RestClient.
    @HttpExchange("/payments")
    interface BankClient {
        @GetExchange("/{id}")
        Payment find(@PathVariable("id") long id);
    }

    @Configuration
    @EnableAutoConfiguration
    @ImportHttpServices(group = "bank", types = BankClient.class) // настройки группы — в spring.http.serviceclient.bank.*
    static class App {}

    public static void main(String[] args) throws Exception {
        System.clearProperty("logback.configurationFile");
        var bank = startBank();
        try (var spring = SpringApplication.run(App.class,
                "--spring.main.web-application-type=none", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--spring.http.clients.imperative.factory=jdk",              // JDK HttpClient под RestClient
                "--spring.http.clients.read-timeout=1s",                     // общий таймаут чтения для клиентов Boot
                "--spring.http.serviceclient.bank.base-url=http://localhost:18081")) {

            var plain = RestClient.create("http://localhost:18081");      // мимо Boot: настроек нет
            time("RestClient.create()", () -> plain.get().uri("/slow").retrieve().body(String.class));

            var configured = spring.getBean(RestClient.Builder.class)        // билдер от Boot с настройками
                    .baseUrl("http://localhost:18081").build();
            time("Boot RestClient.Builder", () -> configured.get().uri("/slow").retrieve().body(String.class));

            time("404 by default", () -> configured.get().uri("/payments/7").retrieve().body(Payment.class));
            time("404 with onStatus", () -> configured.get().uri("/payments/7").retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new PaymentNotFoundException(7);          // ошибка на языке своего домена
                    })
                    .body(Payment.class));

            var bankClient = spring.getBean(BankClient.class);
            time("BankClient.find(42)", () -> bankClient.find(42));
            time("BankClient.find(7)", () -> bankClient.find(7));
        }
        bank.stop(0);
    }

    static void time(String label, Callable<Object> call) {
        long start = System.nanoTime();
        String result;
        try {
            result = "-> " + call.call();
        } catch (Exception e) {
            result = "!! " + e.getClass().getName().replaceAll(".*\\.", "") + ": " + e.getMessage();
        }
        long tenths = (System.nanoTime() - start) / 100_000_000;
        System.out.printf("%-26s %.1f s %s%n", label, tenths / 10.0, result);
    }

    // «Банк»: /slow отвечает через 3 секунды, /payments/42 есть, остальные — 404.
    static HttpServer startBank() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(18081), 0);
        server.createContext("/slow", exchange -> {
            sleep(3000);
            send(exchange, 200, "slow ok");
        });
        server.createContext("/payments/", exchange -> {
            String id = exchange.getRequestURI().getPath().replace("/payments/", "");
            if (id.equals("42")) send(exchange, 200, "{\"id\":42,\"status\":\"DONE\"}");
            else send(exchange, 404, "{\"error\":\"payment " + id + " not found\"}");
        });
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", body.startsWith("{") ? "application/json" : "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
