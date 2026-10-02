package kb.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Запуск: ./run.sh SecurityDemo         — настройки Boot по умолчанию
//         ./run.sh SecurityDemo custom  — две цепочки фильтров
public class SecurityDemo {

    @RestController
    static class PaymentController {
        @GetMapping("/api/payments/1")
        String get() { return "payment 1"; }

        @PostMapping("/api/payments")
        String create() { return "created"; }

        @GetMapping("/api/admin/stats")
        String stats() { return "stats"; }

        @GetMapping("/api/whoami")
        String whoami() throws Exception {
            String here = name();
            // Другой поток: SecurityContextHolder хранит контекст в ThreadLocal.
            String other = CompletableFuture.supplyAsync(SecurityDemo::name).get();
            return "request thread=" + here + ", pool thread=" + other;
        }
    }

    static String name() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "null" : a.getName();
    }

    // Без component scan: иначе CustomSecurity попала бы и в запуск по умолчанию.
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(PaymentController.class)
    static class App {}

    @Configuration
    static class CustomSecurity {
        @Bean
        @Order(1) // Проверяется первой; ловит только /api/**.
        SecurityFilterChain api(HttpSecurity http) throws Exception {
            http.securityMatcher("/api/**")
                .authorizeHttpRequests(a -> a
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(c -> c.disable()) // Нет cookie-сессии — нечего подделывать.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults()); // Возьмёт бин CorsConfigurationSource.
            return http.build();
        }

        @Bean
        @Order(2) // Всё остальное: страницы с формой входа и CSRF.
        SecurityFilterChain web(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
            return http.build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() { // Имя бина важно: по нему его ищет .cors().
            var c = new CorsConfiguration();
            c.setAllowedOrigins(List.of("https://shop.example"));
            c.setAllowedMethods(List.of("GET", "POST"));
            c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            var source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/**", c);
            return source;
        }

        @Bean
        InMemoryUserDetailsManager users() {
            // {noop} — пароль без хеша, только для демо.
            return new InMemoryUserDetailsManager(
                User.withUsername("user").password("{noop}secret").roles("USER").build(),
                User.withUsername("admin").password("{noop}secret").roles("ADMIN").build());
        }
    }

    static final HttpClient http = HttpClient.newHttpClient();
    static String base;

    static String call(String method, String path, String user, Map<String, String> headers) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path)).method(method, HttpRequest.BodyPublishers.noBody());
        if (user != null) {
            b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":secret").getBytes()));
        }
        headers.forEach(b::header);
        var res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        String extra = "";
        for (String h : List.of("WWW-Authenticate", "Location", "Access-Control-Allow-Origin")) {
            var v = res.headers().firstValue(h);
            if (v.isPresent()) extra += " " + h + ": " + v.get().replaceAll("http://localhost:\\d+", "");
        }
        String body = res.body().length() > 60 ? "" : " " + res.body();
        return res.statusCode() + extra + body;
    }

    static String call(String method, String path, String user) throws Exception {
        return call(method, path, user, Map.of());
    }

    static void chains(ConfigurableApplicationContext ctx) {
        var proxy = ctx.getBean("springSecurityFilterChain", FilterChainProxy.class);
        for (SecurityFilterChain chain : proxy.getFilterChains()) {
            System.out.println("chain: " + chain.getFilters().size() + " filters");
            chain.getFilters().forEach(f -> System.out.println("  " + f.getClass().getSimpleName()));
        }
    }

    public static void main(String[] args) throws Exception {
        boolean custom = args.length > 0 && args[0].equals("custom");
        var app = custom ? new SpringApplication(App.class, CustomSecurity.class) : new SpringApplication(App.class);
        app.setDefaultProperties(Map.of("server.port", "0", "spring.main.banner-mode", "off",
                "logging.level.root", "warn", "logging.level.org.springframework.security", System.getProperty("seclog", "warn"), "spring.security.user.password", "secret"));
        try (var ctx = app.run()) {
            base = "http://localhost:" + ((WebServerApplicationContext) ctx).getWebServer().getPort();
            Map<String, String> preflight = Map.of("Origin", "https://shop.example",
                    "Access-Control-Request-Method", "POST");
            if (!custom) {
                chains(ctx);
                System.out.println("GET  no auth, JSON  -> " + call("GET", "/api/payments/1", null, Map.of("Accept", "application/json")));
                System.out.println("GET  no auth, HTML  -> " + call("GET", "/api/payments/1", null, Map.of("Accept", "text/html")));
                System.out.println("GET  user           -> " + call("GET", "/api/payments/1", "user"));
                System.out.println("POST user           -> " + call("POST", "/api/payments", "user"));
                System.out.println("OPTIONS preflight   -> " + call("OPTIONS", "/api/payments", null, preflight));
            } else {
                System.out.println("POST user           -> " + call("POST", "/api/payments", "user"));
                System.out.println("GET  admin as user  -> " + call("GET", "/api/admin/stats", "user"));
                System.out.println("GET  admin as admin -> " + call("GET", "/api/admin/stats", "admin"));
                System.out.println("OPTIONS preflight   -> " + call("OPTIONS", "/api/payments", null, preflight));
                System.out.println("OPTIONS evil origin -> " + call("OPTIONS", "/api/payments", null,
                        Map.of("Origin", "https://evil.example", "Access-Control-Request-Method", "POST")));
                System.out.println("GET  /home no auth  -> " + call("GET", "/home", null, Map.of("Accept", "text/html")));
                System.out.println("GET  whoami         -> " + call("GET", "/api/whoami", "user"));
                chains(ctx);
            }
        }
    }
}
