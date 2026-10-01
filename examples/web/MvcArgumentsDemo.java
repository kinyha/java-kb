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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

// Аргумент без аннотации, имя параметра без -parameters и String как результат метода.
// Запуск через ./run.sh компилирует файл без -parameters.
public class MvcArgumentsDemo {
    static final Logger TOMCAT_LOG = Logger.getLogger("org.apache");
    static final Logger SPRING_LOG = Logger.getLogger("org.springframework");

    record PaymentRequest(String account, Long amount) {}

    @RestController
    static class PaymentController {
        @PostMapping("/with-body")
        String withBody(@RequestBody PaymentRequest request) {
            return request.toString();
        }

        @PostMapping("/without-body")
        String withoutBody(PaymentRequest request) { // Забыли @RequestBody: Spring ищет поля в параметрах запроса.
            return request.toString();
        }

        @GetMapping("/named/{id}")
        String named(@PathVariable("id") long id) { // Имя задано явно.
            return "id=" + id;
        }

        @GetMapping("/unnamed/{id}")
        String unnamed(@PathVariable long id) { // Имя берётся из байткода, только если компилировали с -parameters.
            return "id=" + id;
        }

        @GetMapping(value = "/status", produces = "application/json")
        String status() { // String пишет StringHttpMessageConverter как есть, без кавычек JSON.
            return "ok";
        }
    }

    @Controller
    static class PageController {
        @GetMapping("/page")
        String page() { // В @Controller строка — имя представления (view), а не тело ответа.
            return "ok";
        }
    }

    static class ExceptionLogger implements HandlerExceptionResolver, Ordered {
        static String last = "-";

        @Override
        public ModelAndView resolveException(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
            last = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return null;
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
        @Bean PageController pageController() { return new PageController(); }
        @Bean ExceptionLogger exceptionLogger() { return new ExceptionLogger(); }
    }

    public static void main(String[] args) throws Exception {
        TOMCAT_LOG.setLevel(Level.OFF);
        SPRING_LOG.setLevel(Level.OFF);
        for (var h : Logger.getLogger("").getHandlers()) h.setLevel(Level.OFF);
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
        call(client, "POST", "/with-body", json);
        call(client, "POST", "/without-body", json);
        call(client, "GET", "/named/42", null);
        call(client, "GET", "/unnamed/42", null);
        call(client, "GET", "/status", null);
        call(client, "GET", "/page", null);

        tomcat.stop();
        tomcat.destroy();
    }

    static void call(HttpClient client, String method, String path, String body) throws Exception {
        ExceptionLogger.last = "-";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18080" + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) request.header("Content-Type", "application/json");
        var response = client.send(request.build(), BodyHandlers.ofString());
        String type = response.headers().firstValue("Content-Type").orElse("-");
        System.out.println(method + " " + path + " -> " + response.statusCode());
        System.out.println("  " + (response.statusCode() < 300 ? "body: " + response.body() + "  (" + type + ")" : ExceptionLogger.last));
    }
}
