import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

// Где Bean Validation срабатывает сама, где её надо включить и какие исключения получаются.
public class ValidationDemo {
    record Recipient(@NotBlank String iban) {}

    record PaymentRequest(@NotBlank String account, @Positive long amount,
                          @Valid Recipient recipient) {} // @Valid: проверять и вложенный объект

    record ShallowRequest(@NotBlank String account, @Positive long amount,
                          Recipient recipient) {}        // без @Valid вложенный объект не проверяется

    @Validated // Spring обернёт бин в прокси, который проверяет аргументы методов
    static class PaymentService {
        long charge(@NotBlank String account, @Positive long amount) {
            return amount;
        }
    }

    @RestController
    static class PaymentController {
        final PaymentService service;

        PaymentController(PaymentService service) {
            this.service = service;
        }

        @PostMapping("/plain")
        @ResponseStatus(HttpStatus.CREATED)
        String plain(@RequestBody PaymentRequest request) { // нет @Valid: никто не проверяет
            return "created " + request;
        }

        @PostMapping("/valid")
        @ResponseStatus(HttpStatus.CREATED)
        String valid(@Valid @RequestBody PaymentRequest request) {
            return "created " + request;
        }

        @GetMapping("/payments/{id}")
        String find(@PathVariable("id") @Positive long id) { // ограничение прямо на параметре
            return "payment " + id;
        }

        @PostMapping("/payments/{id}/refund")
        String refund(@PathVariable("id") @Positive long id, @Valid @RequestBody PaymentRequest request) {
            return "refund " + id;
        }

        @PostMapping("/service")
        String viaService(@RequestBody PaymentRequest request) { // проверку делает прокси сервиса
            return "charged " + service.charge(request.account(), request.amount());
        }
    }

    @RestController
    @Validated // старый способ до Spring 6.1: контроллер оборачивается в прокси
    static class LegacyController {
        @GetMapping("/legacy/payments/{id}")
        String find(@PathVariable("id") @Positive long id) {
            return "payment " + id;
        }
    }

    // Первым видит исключение, печатает его и отдаёт стандартным обработчикам.
    static class ExceptionLogger implements HandlerExceptionResolver, Ordered {
        @Override
        public ModelAndView resolveException(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
            System.out.println("  exception: " + ex.getClass().getSimpleName());
            return null;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({PaymentController.class, LegacyController.class, PaymentService.class, ExceptionLogger.class})
    static class App {}

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ENGLISH); // сообщения валидатора зависят от локали

        // 1. Валидатор сам по себе: проверка происходит только при явном вызове.
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var deep = new PaymentRequest(" ", -500, new Recipient(""));
        var shallow = new ShallowRequest(" ", -500, new Recipient(""));
        System.out.println("object created, nothing checked yet: " + deep);
        System.out.println("with @Valid on recipient:");
        validator.validate(deep).stream().map(v -> "  " + v.getPropertyPath() + ": " + v.getMessage())
                .sorted().forEach(System.out::println);
        System.out.println("without @Valid on recipient:");
        validator.validate(shallow).stream().map(v -> "  " + v.getPropertyPath() + ": " + v.getMessage())
                .sorted().forEach(System.out::println);

        // 2. Те же правила в Spring MVC.
        System.clearProperty("logback.configurationFile"); // дальше логи настраивает Boot
        try (var spring = SpringApplication.run(App.class, "--server.port=18080", "--spring.main.banner-mode=off",
                "--logging.level.root=ERROR", "--logging.level.org.apache.catalina=OFF")) { // без стека 500 в выводе
            System.out.println("service bean class: " + spring.getBean(PaymentService.class).getClass().getSimpleName());
            var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            String bad = "{\"account\":\" \",\"amount\":-500,\"recipient\":{\"iban\":\"\"}}";
            call(client, "POST", "/plain", bad);
            call(client, "POST", "/valid", bad);
            call(client, "GET", "/payments/-1", null);
            call(client, "GET", "/legacy/payments/-1", null);
            call(client, "POST", "/payments/-1/refund", bad);
            call(client, "POST", "/service", bad);
        }
    }

    static void call(HttpClient client, String method, String path, String body) throws Exception {
        System.out.println(method + " " + path);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18080" + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(request.build(), BodyHandlers.ofString());
        System.out.println("  " + response.statusCode() + " " + response.body());
    }
}
