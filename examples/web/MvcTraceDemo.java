import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.IntStream;
import java.util.logging.Logger;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

// Путь одного POST /payments: фильтр → DispatcherServlet → интерцептор → конвертеры → контроллер.
public class MvcTraceDemo {
    static final Logger TOMCAT_LOG = Logger.getLogger("org.apache");
    static final Logger SPRING_LOG = Logger.getLogger("org.springframework");

    record PaymentRequest(String account, long amount) {}
    record Payment(long id, String account, long amount, String status) {}

    @RestController
    @RequestMapping("/payments")
    static class PaymentController {
        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        Payment create(@RequestBody PaymentRequest request) {
            System.out.println("      controller: create(" + request + ")");
            return new Payment(42, request.account(), request.amount(), "NEW");
        }

        @GetMapping
        List<Payment> list(@RequestParam("limit") int limit) { // Большой ответ не помещается в буфер 8 КБ.
            System.out.println("      controller: list(" + limit + ")");
            return IntStream.range(0, limit).mapToObj(i -> new Payment(i, "acc-" + i, 100, "NEW")).toList();
        }
    }

    // Фильтр Tomcat: стоит снаружи DispatcherServlet и не знает, какой контроллер будет вызван.
    static class TraceFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            System.out.println("filter: before " + ((HttpServletRequest) req).getRequestURI());
            chain.doFilter(req, res);
            System.out.println("filter: after");
        }
    }

    // Интерцептор Spring MVC: вызывается DispatcherServlet и уже знает выбранный метод.
    static class TraceInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
            System.out.println("  preHandle: handler = " + handler);
            return true; // false остановит обработку: контроллер не вызовется
        }

        @Override
        public void postHandle(HttpServletRequest req, HttpServletResponse res, Object handler, ModelAndView mv) {
            System.out.println("  postHandle: committed=" + res.isCommitted() + ", modelAndView=" + mv);
        }

        @Override
        public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
            System.out.println("  afterCompletion: status=" + res.getStatus() + ", exception=" + ex);
        }
    }

    // Точки расширения вокруг чтения и записи тела показывают, какой конвертер выбран.
    @ControllerAdvice
    static class BodyTrace extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {
        @Override
        public boolean supports(MethodParameter p, Type type, Class<? extends HttpMessageConverter<?>> converter) {
            return true;
        }

        @Override
        public Object afterBodyRead(Object body, HttpInputMessage msg, MethodParameter p, Type type,
                                    Class<? extends HttpMessageConverter<?>> converter) {
            System.out.println("    body read by " + converter.getSimpleName() + " as " + body.getClass().getSimpleName());
            return body;
        }

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converter) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                      Class<? extends HttpMessageConverter<?>> converter,
                                      ServerHttpRequest req, ServerHttpResponse res) {
            System.out.println("    body write by " + converter.getSimpleName() + " as " + contentType);
            return body;
        }
    }

    @Configuration
    @EnableWebMvc // В Boot вместо этого работает WebMvcAutoConfiguration.
    static class WebConfig implements WebMvcConfigurer {
        @Bean PaymentController paymentController() { return new PaymentController(); }
        @Bean BodyTrace bodyTrace() { return new BodyTrace(); }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new TraceInterceptor());
        }
    }

    static List<Object> sorted(Collection<?> beans) {
        var list = new ArrayList<Object>(beans);
        AnnotationAwareOrderComparator.sort(list);
        return list;
    }

    static List<String> names(Collection<?> objects) {
        return objects.stream().map(o -> o.getClass().getSimpleName()).toList();
    }

    public static void main(String[] args) throws Exception {
        TOMCAT_LOG.setLevel(Level.SEVERE);
        SPRING_LOG.setLevel(Level.WARNING);
        for (var h : Logger.getLogger("").getHandlers()) h.setLevel(Level.WARNING); // Без служебных INFO-строк.
        var spring = new AnnotationConfigWebApplicationContext();
        spring.register(WebConfig.class);

        var tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("tomcat").toString());
        tomcat.setPort(18080);
        tomcat.getConnector();
        var ctx = tomcat.addContext("", null);
        var dispatcherServlet = new DispatcherServlet(spring);
        var dispatcher = Tomcat.addServlet(ctx, "dispatcher", dispatcherServlet); // Обычный сервлет Tomcat.
        dispatcher.setLoadOnStartup(1); // Поднять Spring-контекст при старте, а не на первом запросе.
        ctx.addServletMapping("/", "dispatcher");
        var def = new FilterDef();
        def.setFilterName("trace");
        def.setFilter(new TraceFilter());
        ctx.addFilterDef(def);
        var map = new FilterMap();
        map.setFilterName("trace");
        map.addURLPattern("/*");
        ctx.addFilterMap(map);
        tomcat.start();

        // Стратегии, которые DispatcherServlet нашёл в контексте, в порядке опроса.
        System.out.println("handler mappings: " + names(dispatcherServlet.getHandlerMappings()));
        System.out.println("handler adapters: " + names(sorted(spring.getBeansOfType(HandlerAdapter.class).values())));
        System.out.println("exception resolvers: " + names(spring.getBean(HandlerExceptionResolverComposite.class).getExceptionResolvers()));
        System.out.println("message converters: " + names(spring.getBean(RequestMappingHandlerAdapter.class).getMessageConverters()));

        // Таблица маршрутов собрана при старте, до первого запроса.
        var mapping = spring.getBean(RequestMappingHandlerMapping.class);
        mapping.getHandlerMethods().entrySet().stream()
                .map(e -> "mapped " + e.getKey() + " -> " + e.getValue()).sorted().forEach(System.out::println);

        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18080/payments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"account\":\"acc-1\",\"amount\":500}"))
                .build();
        var response = client.send(request, BodyHandlers.ofString());
        System.out.println("client: " + response.statusCode() + " " + response.body());

        var big = client.send(HttpRequest.newBuilder(URI.create("http://localhost:18080/payments?limit=300")).build(),
                BodyHandlers.ofString());
        System.out.println("client: " + big.statusCode() + ", " + big.body().length() + " chars of JSON");

        tomcat.stop();
        tomcat.destroy();
    }
}
