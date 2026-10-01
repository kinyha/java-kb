import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// WebFlux на Netty с двумя потоками event loop: неблокирующее ожидание, блокировка и вынос блокировки.
public class ReactiveLoopDemo {
    static String thread() {
        return Thread.currentThread().getName();
    }

    @RestController
    static class BankController {
        @GetMapping("/delay")
        Mono<String> delay() {                    // ждём секунду, не занимая поток
            String handler = thread();
            return Mono.delay(Duration.ofSeconds(1)).map(tick -> handler + " -> " + thread());
        }

        @GetMapping("/sleep")
        Mono<String> sleep() {                    // ошибка: блокирующий вызов в потоке event loop
            String handler = thread();
            sleepMillis(1000);
            return Mono.just(handler + " -> " + thread());
        }

        @GetMapping("/offload")
        Mono<String> offload() {                  // блокирующий вызов вынесен в отдельный пул
            String handler = thread();
            return Mono.fromCallable(() -> {
                sleepMillis(1000);
                return handler + " -> " + thread();
            }).subscribeOn(Schedulers.boundedElastic());
        }

        @GetMapping("/health")
        Mono<String> health() {
            return Mono.just(thread());
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @Import(BankController.class)
    static class App {}

    record Result(double seconds, String body) {}

    public static void main(String[] args) throws Exception {
        System.setProperty("reactor.netty.ioWorkerCount", "2"); // по умолчанию max(число ядер, 4)
        System.clearProperty("logback.configurationFile");
        try (var spring = SpringApplication.run(App.class, "--server.port=18080", "--spring.main.banner-mode=off",
                "--logging.level.root=ERROR", "--spring.main.web-application-type=reactive")) {
            var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            for (var path : List.of("/delay", "/sleep", "/offload")) {
                var calls = new ArrayList<CompletableFuture<Result>>();
                for (int i = 0; i < 6; i++) calls.add(call(client, path)); // шесть запросов разом
                Thread.sleep(100);
                var health = call(client, "/health");                      // и health-check через 0,1 с
                var times = new ArrayList<String>();
                var handlers = new TreeSet<String>();
                var results = new TreeSet<String>();
                for (var c : calls) {
                    var r = c.join();
                    times.add(String.format("%.1f", r.seconds()));
                    handlers.add(r.body().split(" -> ")[0]);
                    results.add(r.body().split(" -> ")[1]);
                }
                times.sort(null);
                System.out.println("6 x " + path + " finished after: " + String.join(" ", times) + " s");
                System.out.println("  handler ran on: " + handlers + ", result produced on: " + results);
                System.out.printf("  /health sent 0.1 s later: %.1f s%n", health.join().seconds());
            }
        }
    }

    static CompletableFuture<Result> call(HttpClient client, String path) {
        long start = System.nanoTime();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18080" + path)).build();
        return client.sendAsync(request, BodyHandlers.ofString())
                .thenApply(r -> new Result((System.nanoTime() - start) / 100_000_000 / 10.0, r.body()));
    }

    static void sleepMillis(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
