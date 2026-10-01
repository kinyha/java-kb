import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Как исключения превращаются в ответы. Запуск: ./run.sh ErrorHandlingDemo [naive]
public class ErrorHandlingDemo {
    static class PaymentException extends RuntimeException {
        PaymentException(String message) { super(message); }
    }

    static class PaymentNotFoundException extends PaymentException {
        final long id;
        PaymentNotFoundException(long id) { super("Payment " + id + " not found"); this.id = id; }
    }

    static class InsufficientFundsException extends PaymentException { // своего обработчика нет
        InsufficientFundsException(String account) { super("Not enough money on " + account); }
    }

    record PaymentRequest(@NotBlank String account, @Positive long amount) {}

    @RestController
    static class PaymentController {
        @GetMapping("/payments/{id}")
        String find(@PathVariable("id") long id) {
            throw new PaymentNotFoundException(id);
        }

        @PostMapping("/payments/{id}/charge")
        String charge(@PathVariable("id") long id) {
            throw new InsufficientFundsException("acc-1");
        }

        @PostMapping("/payments")
        String create(@Valid @RequestBody PaymentRequest request) {
            return "created";
        }

        @GetMapping("/payments/report")
        String report() { // текст исключения не должен уйти клиенту
            throw new IllegalStateException("connection to db-prod:5432 refused for user payments");
        }

        @GetMapping("/payments/archive")
        String archive() { // исключение, которое уже несёт статус
            throw new ResponseStatusException(HttpStatus.GONE, "Payments before 2020 are archived");
        }
    }

    @RestController
    static class RefundController {
        @GetMapping("/refunds/{id}")
        String find(@PathVariable("id") long id) {
            throw new IllegalStateException("Refund " + id + " is locked");
        }

        @ExceptionHandler(IllegalStateException.class) // свой обработчик контроллера проверяется раньше advice
        ResponseEntity<String> locked(IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("local handler: " + ex.getMessage());
        }
    }

    // Правильный вариант: база ResponseEntityExceptionHandler отвечает за исключения Spring MVC.
    @RestControllerAdvice
    static class ApiErrors extends ResponseEntityExceptionHandler {
        @ExceptionHandler(PaymentNotFoundException.class)
        ProblemDetail notFound(PaymentNotFoundException ex) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
            problem.setTitle("Payment not found");
            problem.setProperty("paymentId", ex.id); // своё поле рядом со стандартными
            return problem;
        }

        @ExceptionHandler(PaymentException.class) // ловит всех наследников без своего обработчика
        ProblemDetail rejected(PaymentException ex) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
            problem.setTitle("Payment rejected");
            return problem;
        }

        @Override // MethodArgumentNotValidException уже обработан в базе: дополняем, а не дублируем
        protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                HttpHeaders headers, HttpStatusCode status, WebRequest request) {
            var problem = ex.getBody(); // ProblemDetail, который Spring уже собрал
            problem.setProperty("errors", ex.getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage()).sorted().toList());
            return ResponseEntity.status(status).body(problem);
        }

        @ExceptionHandler(Exception.class) // всё остальное: 500 без подробностей
        ProblemDetail unexpected(Exception ex) {
            System.out.println("  log: " + ex); // в настоящем сервисе — log.error с id запроса
            return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        }
    }

    // Наивный вариант: один обработчик на все исключения.
    @RestControllerAdvice
    static class NaiveErrors {
        @ExceptionHandler(Exception.class)
        ProblemDetail any(Exception ex) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        }
    }

    // Фильтр работает до DispatcherServlet, поэтому @ControllerAdvice его исключений не видит.
    static class TenantFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
            throw new IllegalStateException("tenant store unavailable");
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({PaymentController.class, RefundController.class})
    static class Common {
        @Bean
        FilterRegistrationBean<TenantFilter> tenantFilter() {
            var registration = new FilterRegistrationBean<>(new TenantFilter());
            registration.addUrlPatterns("/tenants/*");
            return registration;
        }
    }

    @Configuration @Import({Common.class, ApiErrors.class}) static class App {}
    @Configuration @Import({Common.class, NaiveErrors.class}) static class NaiveApp {}

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ENGLISH);
        System.clearProperty("logback.configurationFile");
        boolean naive = args.length > 0 && args[0].equals("naive");
        try (var spring = SpringApplication.run(naive ? NaiveApp.class : App.class, "--server.port=18080",
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR", "--logging.level.org.apache.catalina=OFF")) {
            System.out.println(naive ? "advice: one @ExceptionHandler(Exception.class)" : "advice: ApiErrors extends ResponseEntityExceptionHandler");
            var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            call(client, "GET", "/payments/42", null);
            call(client, "POST", "/payments/42/charge", null);
            call(client, "POST", "/payments", "{\"account\":\" \",\"amount\":-500}");
            call(client, "GET", "/payments/report", null);
            call(client, "GET", "/payments/archive", null);
            call(client, "DELETE", "/payments/42", null);
            call(client, "GET", "/refunds/7", null);
            call(client, "GET", "/tenants/1", null);
        }
    }

    static void call(HttpClient client, String method, String path, String body) throws Exception {
        System.out.println(method + " " + path);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18080" + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(request.build(), BodyHandlers.ofString());
        System.out.println("  " + response.statusCode() + " " + response.headers().firstValue("Content-Type").orElse("-")
                + " " + response.body());
    }
}
