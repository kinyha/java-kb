package kb.boot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

public class StartupDemo {
    static final CountDownLatch runnerEntered = new CountDownLatch(1);
    static final CountDownLatch releaseRunner = new CountDownLatch(1);
    static final AtomicBoolean warmed = new AtomicBoolean();
    static final AtomicInteger port = new AtomicInteger();
    static final AtomicReference<ConfigurableApplicationContext> context = new AtomicReference<>();

    @RestController
    public static class PaymentController {
        @GetMapping("/pay")
        String pay() { return "warm=" + warmed.get(); } // Readiness само не блокирует маршруты.
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(PaymentController.class)
    public static class Application {
        @Bean
        ApplicationRunner warmup() {
            return args -> {
                System.out.println("runner entered");
                runnerEntered.countDown();
                if (!releaseRunner.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("driver timeout");
                if (args.containsOption("fail-warmup")) throw new IllegalStateException("bank warmup failed");
                warmed.set(true);
                System.out.println("runner finished");
            };
        }
    }

    static SpringApplication application(Class<?> source) {
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);
        SpringApplication app = new SpringApplication(source);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of("server.port", "0", "logging.level.root", "OFF",
                "spring.main.register-shutdown-hook", "false"));
        return app;
    }

    static HttpResponse<String> get(HttpClient client, int serverPort, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serverPort + path))
                .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("loader=" + StartupDemo.class.getClassLoader().getClass().getName());
        SpringApplication app = application(Application.class);
        AtomicBoolean ready = new AtomicBoolean();
        app.addListeners(event -> {
            if (event.getClass().getSimpleName().equals("ServletWebServerInitializedEvent")) {
                // Получаем порт после refresh через контекст; слушатель здесь фиксирует порядок.
                System.out.println("server initialized");
            }
            if (event instanceof ApplicationStartedEvent started) {
                context.set(started.getApplicationContext());
                var server = (org.springframework.boot.web.server.context.WebServerApplicationContext)
                        started.getApplicationContext();
                port.set(server.getWebServer().getPort());
                System.out.println("Started");
            }
            if (event instanceof ApplicationReadyEvent) {
                ready.set(true);
                System.out.println("Ready");
            }
        });
        var launch = new CompletableFuture<ConfigurableApplicationContext>();
        Thread startupThread = new Thread(() -> {
            try { launch.complete(app.run(args)); }
            catch (Throwable failure) { launch.completeExceptionally(failure); }
        }, "boot-launch");
        // В executable jar ресурсы зависимостей видит загрузчик Boot.
        // Обычный worker commonPool использует системный TCCL и не видит nested JAR.
        startupThread.setContextClassLoader(StartupDemo.class.getClassLoader());
        startupThread.start();
        if (!runnerEntered.await(15, TimeUnit.SECONDS)) {
            launch.join();
            throw new IllegalStateException("runner was not called");
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            var early = get(client, port.get(), "/pay");
            System.out.println("before Ready: HTTP " + early.statusCode() + ", " + early.body());
            var availability = context.get().getBean(ApplicationAvailability.class);
            System.out.println("before readiness=" + availability.getReadinessState());
            releaseRunner.countDown();
            try (var started = launch.get(15, TimeUnit.SECONDS)) {
                var after = get(client, port.get(), "/pay");
                System.out.println("after Ready: HTTP " + after.statusCode() + ", " + after.body());
                System.out.println("after readiness=" + availability.getReadinessState());
            } catch (java.util.concurrent.ExecutionException ex) {
                System.out.println("startup failed, Ready=" + ready.get());
            }
        } finally {
            releaseRunner.countDown();
            if (context.get() != null) context.get().close();
        }
    }
}
