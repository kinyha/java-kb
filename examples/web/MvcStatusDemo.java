import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.catalina.startup.Tomcat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

// На каком этапе DispatcherServlet отказал запросу — видно по статусу и исключению.
public class MvcStatusDemo {
    static final Logger TOMCAT_LOG = Logger.getLogger("org.apache");
    static final Logger SPRING_LOG = Logger.getLogger("org.springframework");

    record PaymentRequest(String account, long amount) {}
    record Payment(long id, String account, long amount) {}

    @RestController
    @RequestMapping("/payments")
    static class PaymentController {
        @GetMapping("/{id}")
        Payment find(@PathVariable("id") long id) {
            return new Payment(id, "acc-1", 500);
        }

        @GetMapping("/export") // Точный путь, который тоже подходит под шаблон /{id}.
        String export() {
            return "export";
        }

        @PostMapping(consumes = "application/json")
        @ResponseStatus(HttpStatus.CREATED)
        Payment create(@RequestBody PaymentRequest request) {
            return new Payment(43, request.account(), request.amount());
        }
    }

    // Первым в цепочке обработчиков ошибок: печатает исключение и отдаёт его стандартным обработчикам.
    static class ExceptionLogger implements HandlerExceptionResolver, Ordered {
        static String last = "-";

        @Override
        public ModelAndView resolveException(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
            last = ex.getClass().getSimpleName();
            return null; // null: «не обработал», DispatcherServlet спросит следующий обработчик
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    @Configuration
    @EnableWebMvc
    static class WebConfig {
        @Bean PaymentController paymentController() { return new PaymentController(); }
        @Bean ExceptionLogger exceptionLogger() { return new ExceptionLogger(); }
    }

    public static void main(String[] args) throws Exception {
        TOMCAT_LOG.setLevel(Level.SEVERE);
        SPRING_LOG.setLevel(Level.SEVERE);
        for (var h : Logger.getLogger("").getHandlers()) h.setLevel(Level.SEVERE);
        var spring = new AnnotationConfigWebApplicationContext();
        spring.register(WebConfig.class);
        var tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("tomcat").toString());
        tomcat.setPort(18080);
        tomcat.getConnector();
        var ctx = tomcat.addContext("", null);
        Tomcat.addServlet(ctx, "dispatcher", new DispatcherServlet(spring)).setLoadOnStartup(1);
        ctx.addServletMapping("/", "dispatcher");
        tomcat.start();

        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        String json = "{\"account\":\"acc-1\",\"amount\":500}";
        call(client, "GET", "/payments/42", null, null, null);
        call(client, "GET", "/payments/export", null, null, null);
        call(client, "GET", "/payments/42/", null, null, null);
        call(client, "GET", "/refunds/42", null, null, null);
        call(client, "DELETE", "/payments/42", null, null, null);
        call(client, "GET", "/payments/abc", null, null, null);
        call(client, "POST", "/payments", "text/plain", null, json);
        call(client, "POST", "/payments", "application/json", null, "{\"account\":");
        call(client, "GET", "/payments/42", null, "application/xml", null);
        call(client, "POST", "/payments", "application/json", null, json);

        tomcat.stop();
        tomcat.destroy();
    }

    static void call(HttpClient client, String method, String path, String contentType, String accept, String body)
            throws Exception {
        ExceptionLogger.last = "-";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18080" + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) request.header("Content-Type", contentType);
        if (accept != null) request.header("Accept", accept);
        var response = client.send(request.build(), BodyHandlers.ofString());
        String extra = (contentType != null ? " [" + contentType + "]" : "") + (accept != null ? " [Accept: " + accept + "]" : "");
        String allow = response.headers().firstValue("Allow").map(a -> ", Allow: " + a).orElse("");
        String shown = response.statusCode() < 300 ? " " + response.body() : " " + ExceptionLogger.last + allow;
        System.out.printf("%-6s %-16s%-27s -> %d%s%n", method, path, extra, response.statusCode(), shown);
    }
}
