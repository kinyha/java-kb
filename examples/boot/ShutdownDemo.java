package kb.boot;

import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

public class ShutdownDemo {
    static final CountDownLatch paymentEntered = new CountDownLatch(1);
    static final CountDownLatch releaseBank = new CountDownLatch(1);
    static final CountDownLatch shutdownEntered = new CountDownLatch(1);
    static final AtomicBoolean paymentFinished = new AtomicBoolean();

    @RestController
    public static class PaymentController {
        @GetMapping("/pay")
        String pay() throws Exception {
            paymentEntered.countDown();
            if (!releaseBank.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("driver timeout");
            paymentFinished.set(true);
            return "paid";
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(PaymentController.class)
    public static class Application {}

    public static void main(String[] args) throws Exception {
        boolean expire = args.length > 0 && args[0].equals("expire");
        var app = StartupDemo.application(Application.class);
        app.addListeners(event -> {
            if (event instanceof AvailabilityChangeEvent<?> change
                    && change.getState() == ReadinessState.REFUSING_TRAFFIC) shutdownEntered.countDown();
        });
        var context = app.run("--server.shutdown=graceful",
                "--spring.lifecycle.timeout-per-shutdown-phase=" + (expire ? "200ms" : "5s"));
        var server = (org.springframework.boot.web.server.context.WebServerApplicationContext) context;
        int port = server.getWebServer().getPort();
        try (HttpClient client = HttpClient.newHttpClient()) {
            var payment = CompletableFuture.supplyAsync(() -> {
                try { return StartupDemo.get(client, port, "/pay"); }
                catch (Exception ex) { throw new java.util.concurrent.CompletionException(ex); }
            });
            if (!paymentEntered.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("request not received");
            System.out.println("payment entered");
            var closing = CompletableFuture.runAsync(context::close);
            if (!shutdownEntered.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("shutdown not started");
            System.out.println("readiness=REFUSING_TRAFFIC");
            if (expire) {
                closing.get(15, TimeUnit.SECONDS);
                System.out.println("context closed, paymentFinished=" + paymentFinished.get());
                releaseBank.countDown(); // Освобождаем учебную обвязку после завершения close.
                payment.handle((response, error) -> null).get(15, TimeUnit.SECONDS);
            } else {
                try {
                    closing.get(100, TimeUnit.MILLISECONDS);
                    throw new IllegalStateException("close returned while payment blocked");
                } catch (TimeoutException expected) {
                    System.out.println("close waits for payment=true");
                }
                releaseBank.countDown(); // Ответ банка приходит в пределах фазы.
                var response = payment.get(10, TimeUnit.SECONDS);
                System.out.println("payment: HTTP " + response.statusCode() + ", " + response.body());
                closing.get(10, TimeUnit.SECONDS);
                System.out.println("context closed, paymentFinished=" + paymentFinished.get());
            }
        } finally {
            releaseBank.countDown();
            context.close();
        }
    }
}
