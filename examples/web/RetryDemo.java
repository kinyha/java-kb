import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

// Повтор помогает при временной ошибке и вредит неидемпотентному запросу.
public class RetryDemo {
    static long start;
    static int attempt;

    static void begin() {
        start = System.nanoTime();
        attempt = 0;
    }

    static String at() { // номер попытки и когда она закончилась
        return String.format("attempt %d ended at %.1f s", ++attempt, (System.nanoTime() - start) / 100_000_000 / 10.0);
    }

    static class BankGateway {
        final RestClient client;

        BankGateway(RestClient.Builder builder) {
            this.client = builder.baseUrl("http://localhost:18081").build(); // билдер Boot: read-timeout 1 с
        }

        // 3 повтора, пауза 200 мс, каждый раз вдвое дольше: 200, 400, 800 мс
        @Retryable(includes = HttpServerErrorException.class, maxRetries = 3, delay = 200, multiplier = 2)
        String status() {
            try {
                String body = client.get().uri("/status").retrieve().body(String.class);
                System.out.println("  " + at() + ": 200 " + body);
                return body;
            } catch (HttpServerErrorException e) {
                System.out.println("  " + at() + ": " + e.getStatusCode());
                throw e;                                              // Retryable увидит исключение и повторит
            }
        }

        // Повтор при таймауте: банк мог успеть списать деньги, просто ответ не дошёл
        @Retryable(includes = ResourceAccessException.class, maxRetries = 2, delay = 1000)
        String charge(String idempotencyKey) {
            try {
                var request = client.post().uri("/charge");
                if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
                String body = request.retrieve().body(String.class);
                System.out.println("  " + at() + ": 200 " + body);
                return body;
            } catch (ResourceAccessException e) {
                System.out.println("  " + at() + ": timeout");
                throw e;
            }
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableResilientMethods // включает обработку @Retryable и @ConcurrencyLimit
    @Import(BankGateway.class)
    static class App {}

    // «Банк»
    static final AtomicInteger statusCalls = new AtomicInteger();
    static final AtomicInteger charges = new AtomicInteger();
    static final Map<String, String> processed = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        System.clearProperty("logback.configurationFile");
        var bank = startBank();
        try (var spring = SpringApplication.run(App.class,
                "--spring.main.web-application-type=none", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--spring.http.clients.imperative.factory=jdk", "--spring.http.clients.read-timeout=1s")) {
            var gateway = spring.getBean(BankGateway.class);

            System.out.println("GET /status: bank answers 503 twice, then 200");
            begin();
            gateway.status();

            System.out.println("POST /charge without Idempotency-Key: bank needs 1.5 s, client waits 1 s");
            begin();
            try {
                gateway.charge(null);
            } catch (ResourceAccessException e) {
                System.out.println("  gave up after 3 attempts");
            }
            Thread.sleep(1600);                                     // дать банку закончить последнее списание
            System.out.println("  charges in bank: " + charges.getAndSet(0));

            System.out.println("POST /charge with Idempotency-Key: pay-42");
            begin();
            gateway.charge("pay-42");
            Thread.sleep(1600);
            System.out.println("  charges in bank: " + charges.get());
        }
        bank.stop(0);
    }

    static HttpServer startBank() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(18081), 0);
        server.createContext("/status", exchange -> {
            if (statusCalls.incrementAndGet() <= 2) send(exchange, 503, "busy");
            else send(exchange, 200, "bank is up");
        });
        server.createContext("/charge", exchange -> {
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            if (key != null && processed.containsKey(key)) {        // этот платёж уже проведён: вернуть тот же ответ
                send(exchange, 200, processed.get(key) + " (replayed)");
                return;
            }
            sleep(1500);                                            // долго списываем
            String result = "charged #" + charges.incrementAndGet();
            if (key != null) processed.put(key, result);
            send(exchange, 200, result);
        });
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException clientGone) {
            // клиент уже ушёл по таймауту, а работа банка сделана
        }
        exchange.close();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
