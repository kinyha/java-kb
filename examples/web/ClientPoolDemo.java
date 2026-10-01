import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Пул соединений клиента — ещё одна очередь: 10 параллельных вызовов банка, который отвечает за 1 секунду.
public class ClientPoolDemo {
    public static void main(String[] args) throws Exception {
        var bank = HttpServer.create(new InetSocketAddress(18081), 0);
        bank.createContext("/work", exchange -> {
            sleep(1000);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        bank.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); // банк обслуживает сколько угодно запросов сразу
        bank.start();

        // Apache HttpClient 5 с пулом по умолчанию — его выберет Boot, если библиотека есть в classpath
        run("http-components, default pool", ClientHttpRequestFactoryBuilder.httpComponents().build());
        // тот же клиент, пул на 20 соединений к одному хосту
        run("http-components, 20 per route", ClientHttpRequestFactoryBuilder.httpComponents()
                .withConnectionManagerCustomizer(pool -> pool.setMaxConnPerRoute(20).setMaxConnTotal(50))
                .build());
        // JDK HttpClient: для HTTP/1.1 открывает новые соединения по мере надобности
        run("jdk", ClientHttpRequestFactoryBuilder.jdk().build());
        bank.stop(0);
    }

    static void run(String label, ClientHttpRequestFactory factory) {
        var client = RestClient.builder().requestFactory(factory).baseUrl("http://localhost:18081").build();
        long start = System.nanoTime();
        var calls = new ArrayList<CompletableFuture<String>>();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) { // 10 запросов одновременно
            for (int i = 0; i < 10; i++) {
                calls.add(CompletableFuture.supplyAsync(() -> {
                    client.get().uri("/work").retrieve().toBodilessEntity();
                    return String.format("%.1f", (System.nanoTime() - start) / 100_000_000 / 10.0);
                }, threads));
            }
        }
        var finished = calls.stream().map(CompletableFuture::join).sorted().toList();
        System.out.printf("%-30s finished after: %s s%n", label, String.join(" ", finished));
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
