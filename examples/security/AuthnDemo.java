package kb.security;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Запуск: ./run.sh AuthnDemo
// Часть 1 — пароли, часть 2 — ProviderManager без веба, часть 3 — вход формой и сессия.
public class AuthnDemo {

    // Своя аутентификация по API-ключу партнёра: отдельный тип токена и провайдер.
    record ApiKeyToken(String key) implements Authentication {
        public String getName() { return key; }
        public java.util.Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() { return List.of(); }
        public Object getCredentials() { return key; }
        public Object getDetails() { return null; }
        public Object getPrincipal() { return key; }
        public boolean isAuthenticated() { return false; }
        public void setAuthenticated(boolean b) { throw new UnsupportedOperationException(); }
    }

    static class ApiKeyProvider implements AuthenticationProvider {
        public Authentication authenticate(Authentication a) {
            if (!"pk_live_42".equals(a.getCredentials())) throw new BadCredentialsException("bad key");
            // Готовый токен: принципал, без пароля, с правами — isAuthenticated() == true.
            return UsernamePasswordAuthenticationToken.authenticated("partner-42", null,
                    AuthorityUtils.createAuthorityList("ROLE_PARTNER"));
        }
        public boolean supports(Class<?> type) { return ApiKeyToken.class.isAssignableFrom(type); }
    }

    static long timeMs(Runnable r, int n) {
        long t = System.nanoTime();
        for (int i = 0; i < n; i++) r.run();
        return (System.nanoTime() - t) / n / 1_000_000;
    }

    static String tryAuth(ProviderManager m, Authentication a) {
        try {
            Authentication r = m.authenticate(a);
            return "OK " + r.getName() + " " + r.getAuthorities() + " credentials=" + r.getCredentials();
        } catch (AuthenticationException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== passwords");
        PasswordEncoder enc = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String h1 = enc.encode("secret"), h2 = enc.encode("secret");
        System.out.println(h1);
        System.out.println(h2); // Та же строка, другая соль — другой хеш.
        System.out.println("matches=" + enc.matches("secret", h1) + ", noop matches=" + enc.matches("secret", "{noop}secret"));
        System.out.println("upgrade {noop}=" + enc.upgradeEncoding("{noop}secret") + ", upgrade bcrypt=" + enc.upgradeEncoding(h1));
        // bcrypt видит только 72 байта: "ы" — два байта UTF-8.
        var bc = new BCryptPasswordEncoder();
        try { bc.encode("ы".repeat(37)); } catch (IllegalArgumentException e) { System.out.println("encode 74 bytes: " + e.getMessage()); }
        String h72 = bc.encode("ы".repeat(36));
        System.out.println("matches 72 bytes + \"zzz\": " + bc.matches("ы".repeat(36) + "zzz", h72));
        for (int cost : new int[] {10, 12, 14}) {
            var b = new BCryptPasswordEncoder(cost);
            String h = b.encode("secret");
            b.matches("secret", h); // прогрев
            System.out.println("bcrypt cost " + cost + ": " + timeMs(() -> b.matches("secret", h), 5) + " ms per check");
        }

        System.out.println("== ProviderManager");
        UserDetails alice = User.withUsername("alice").password(enc.encode("secret")).roles("USER").build();
        var dao = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(alice));
        dao.setPasswordEncoder(enc);
        var manager = new ProviderManager(dao, new ApiKeyProvider());
        System.out.println("alice/secret   -> " + tryAuth(manager, UsernamePasswordAuthenticationToken.unauthenticated("alice", "secret")));
        System.out.println("alice/wrong    -> " + tryAuth(manager, UsernamePasswordAuthenticationToken.unauthenticated("alice", "wrong")));
        System.out.println("nobody/secret  -> " + tryAuth(manager, UsernamePasswordAuthenticationToken.unauthenticated("nobody", "secret")));
        System.out.println("api key        -> " + tryAuth(manager, new ApiKeyToken("pk_live_42")));
        // Одинаковое время для существующего и несуществующего логина.
        System.out.println("time wrong password: " + timeMs(() -> tryAuth(manager, UsernamePasswordAuthenticationToken.unauthenticated("alice", "x")), 5) + " ms");
        System.out.println("time unknown user:   " + timeMs(() -> tryAuth(manager, UsernamePasswordAuthenticationToken.unauthenticated("nobody", "x")), 5) + " ms");

        System.out.println("== form login and session");
        var app = new SpringApplication(WebApp.class);
        app.setDefaultProperties(Map.of("server.port", "0", "spring.main.banner-mode", "off", "logging.level.root", "warn"));
        try (var ctx = app.run()) {
            String base = "http://localhost:" + ((WebServerApplicationContext) ctx).getWebServer().getPort();
            var cookies = new CookieManager();
            var http = HttpClient.newBuilder().cookieHandler(cookies).build();
            // 1. Страница входа: сервер создаёт сессию для CSRF-токена.
            String page = http.send(HttpRequest.newBuilder(URI.create(base + "/login")).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            var m = Pattern.compile("name=\"_csrf\" type=\"hidden\" value=\"([^\"]+)\"").matcher(page);
            m.find();
            String before = session(cookies);
            // 2. Отправка формы с логином, паролем и токеном.
            String form = "username=alice&password=secret&_csrf=" + URLEncoder.encode(m.group(1), StandardCharsets.UTF_8);
            var res = http.send(HttpRequest.newBuilder(URI.create(base + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            String after = session(cookies);
            System.out.println("POST /login -> " + res.statusCode() + " Location: " + res.headers().firstValue("Location").orElse("").replaceAll("http://localhost:\\d+", ""));
            System.out.println("session before login: " + before);
            System.out.println("session after login:  " + after + (before.equals(after) ? " (same)" : " (changed)"));
            // 3. Следующий запрос узнаёт пользователя только по cookie сессии.
            System.out.println("GET /me -> " + http.send(HttpRequest.newBuilder(URI.create(base + "/me")).build(),
                    HttpResponse.BodyHandlers.ofString()).body());
        }
    }

    static String session(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                .map(c -> c.getValue().substring(0, 8) + "…").findFirst().orElse("none");
    }

    @RestController
    static class MeController {
        @GetMapping("/me")
        String me(Authentication a) { return a.getName() + " " + a.getAuthorities(); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(MeController.class)
    static class WebApp {
        @Bean
        InMemoryUserDetailsManager users() {
            PasswordEncoder enc = PasswordEncoderFactories.createDelegatingPasswordEncoder();
            return new InMemoryUserDetailsManager(
                    User.withUsername("alice").password(enc.encode("secret")).roles("USER").build());
        }
    }
}
