import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

// То же в Spring Boot 4: ленивый DispatcherServlet, его стратегии и ответ на неизвестный путь.
public class MvcBootDemo {
    @RestController
    static class PaymentController {
        @GetMapping("/payments/{id}")
        String find(@PathVariable("id") long id) {
            return "Платёж " + id; // String уходит как есть, даже если клиент просил JSON
        }
    }

    // Первым видит исключение, печатает его и отдаёт стандартным обработчикам.
    static class ExceptionLogger implements HandlerExceptionResolver, Ordered {
        @Override
        public ModelAndView resolveException(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
            System.out.println("  exception: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    @Configuration
    @EnableAutoConfiguration // Boot сам регистрирует DispatcherServlet и стратегии; @EnableWebMvc не нужен.
    @Import({PaymentController.class, ExceptionLogger.class})
    static class App {}

    public static void main(String[] args) throws Exception {
        System.clearProperty("logback.configurationFile"); // run.sh задаёт его для демо без Boot; Boot настраивает логи сам
        try (var spring = SpringApplication.run(App.class, "--server.port=18080",
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR")) {
            var dispatcher = spring.getBean(DispatcherServlet.class);
            System.out.println("handler mappings before first request: " + dispatcher.getHandlerMappings());

            var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            for (var path : new String[] {"/payments/1", "/refunds/42"}) {
                var request = HttpRequest.newBuilder(URI.create("http://localhost:18080" + path))
                        .header("Accept", "application/json").build();
                System.out.println("GET " + path);
                var response = client.send(request, BodyHandlers.ofString());
                System.out.println("  " + response.statusCode() + " " + response.headers().firstValue("Content-Type").orElse("-")
                        + " " + response.body());
            }
            System.out.println("handler mappings after: " + dispatcher.getHandlerMappings().stream()
                    .map(m -> m.getClass().getSimpleName()).toList());
        }
    }
}
