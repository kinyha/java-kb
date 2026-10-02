package kb.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Запуск: ./run.sh AuthzDemo         — правила URL в неправильном порядке
//         ./run.sh AuthzDemo fixed   — узкое правило первым
public class AuthzDemo {

    record Payment(String id, String owner, int amount) {}

    @Service
    static class AccountService {
        // Поддержка видит любой счёт, клиент — только свой.
        @PreAuthorize("hasRole('SUPPORT') or #id == authentication.name")
        public String balance(String id) { return id + ": 1200"; }

        // Внутренний вызов this.balance идёт мимо прокси — проверки нет.
        public String summary(String id) { return "summary of " + balance(id); }

        // Проверка после вызова: метод уже выполнился, отдать ли результат.
        @PostAuthorize("returnObject.owner() == authentication.name")
        public Payment payment(String id) {
            return new Payment(id, id.equals("p-1") ? "alice" : "bob", 500);
        }
    }

    @RestController
    static class Api {
        private final AccountService accounts;
        Api(AccountService accounts) { this.accounts = accounts; }

        @GetMapping("/api/payments/admin/report")
        String report() { return "report"; }

        @GetMapping("/api/payments/{id}")
        String payment(@PathVariable String id) { return accounts.payment(id).toString(); }

        @GetMapping("/api/accounts/{id}/balance")
        String balance(@PathVariable String id) { return accounts.balance(id); }

        @GetMapping("/api/accounts/{id}/summary")
        String summary(@PathVariable String id) { return accounts.summary(id); }
    }

    @Configuration
    @EnableMethodSecurity // Включает @PreAuthorize и @PostAuthorize.
    static class Security {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            boolean fixed = Boolean.getBoolean("fixed");
            http.authorizeHttpRequests(a -> {
                    if (fixed) a.requestMatchers("/api/payments/admin/**").hasRole("ADMIN");
                    a.requestMatchers("/api/payments/**").authenticated(); // Шире, чем admin/**.
                    if (!fixed) a.requestMatchers("/api/payments/admin/**").hasRole("ADMIN"); // Не сработает.
                    a.anyRequest().authenticated();
                })
                .httpBasic(Customizer.withDefaults())
                .csrf(c -> c.disable());
            return http.build();
        }

        @Bean
        InMemoryUserDetailsManager users() {
            return new InMemoryUserDetailsManager(
                User.withUsername("alice").password("{noop}secret").roles("USER").build(),
                User.withUsername("bob").password("{noop}secret").roles("SUPPORT").build(),
                // authorities("ADMIN") — без префикса ROLE_.
                User.withUsername("ops").password("{noop}secret").authorities("ADMIN").build(),
                User.withUsername("admin").password("{noop}secret").roles("ADMIN").build());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({Api.class, AccountService.class, Security.class})
    static class App {}

    static final HttpClient http = HttpClient.newHttpClient();
    static String base;

    static String get(String path, String user) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":secret").getBytes()))
                .build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() + (res.statusCode() == 200 ? " " + res.body() : "");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("fixed")) System.setProperty("fixed", "true");
        var app = new SpringApplication(App.class);
        app.setDefaultProperties(Map.of("server.port", "0", "spring.main.banner-mode", "off", "logging.level.root", "warn"));
        try (var ctx = app.run()) {
            base = "http://localhost:" + ((WebServerApplicationContext) ctx).getWebServer().getPort();
            System.out.println("report as alice      -> " + get("/api/payments/admin/report", "alice"));
            System.out.println("report as ops        -> " + get("/api/payments/admin/report", "ops"));
            System.out.println("report as admin      -> " + get("/api/payments/admin/report", "admin"));
            if (Boolean.getBoolean("fixed")) return;
            System.out.println("alice balance alice  -> " + get("/api/accounts/alice/balance", "alice"));
            System.out.println("alice balance bob    -> " + get("/api/accounts/bob/balance", "alice"));
            System.out.println("bob   balance alice  -> " + get("/api/accounts/alice/balance", "bob"));
            System.out.println("alice summary bob    -> " + get("/api/accounts/bob/summary", "alice"));
            System.out.println("alice payment p-1    -> " + get("/api/payments/p-1", "alice"));
            System.out.println("alice payment p-2    -> " + get("/api/payments/p-2", "alice"));
        }
    }
}
