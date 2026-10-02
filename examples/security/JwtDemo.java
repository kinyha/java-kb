package kb.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Запуск: ./run.sh JwtDemo
// Демо само играет роль сервера авторизации: создаёт RSA-ключ и подписывает токены.
public class JwtDemo {

    static final String ISSUER = "https://auth.payments.example";
    static RSAKey key;

    // Токен как у Keycloak: iss, sub, aud, exp, scope и свой claim roles.
    static String token(String scope, String aud, long expiresInSec, List<String> roles) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("alice").audience(aud)
                .issueTime(Date.from(Instant.now().plusSeconds(expiresInSec - 300))) // Живёт 5 минут.
                .expirationTime(Date.from(Instant.now().plusSeconds(expiresInSec)))
                .claim("scope", scope).claim("roles", roles).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    // Подмена scope в payload без новой подписи.
    static String tamper(String jwt) {
        String[] p = jwt.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(p[1])).replace("payments.read", "payments.admin");
        return p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." + p[2];
    }

    @RestController
    static class Api {
        @GetMapping("/api/payments")
        String payments() { return "payments"; }

        @GetMapping("/api/refunds")
        String refunds() { return "refunds"; }

        @GetMapping("/api/me")
        String me(Authentication a) { return a.getName() + " " + a.getAuthorities(); }
    }

    @Configuration
    static class Security {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a
                    .requestMatchers("/api/payments").hasAuthority("SCOPE_payments.read")
                    .requestMatchers("/api/refunds").hasRole("SUPPORT")
                    .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter())));
            return http.build();
        }

        // scope -> SCOPE_*, а claim roles -> ROLE_*.
        JwtAuthenticationConverter converter() {
            var scopes = new JwtGrantedAuthoritiesConverter();
            var roles = new JwtGrantedAuthoritiesConverter();
            roles.setAuthoritiesClaimName("roles");
            roles.setAuthorityPrefix("ROLE_");
            var c = new JwtAuthenticationConverter();
            c.setJwtGrantedAuthoritiesConverter(jwt -> {
                var all = new java.util.ArrayList<>(scopes.convert(jwt));
                all.addAll(roles.convert(jwt));
                return all;
            });
            return c;
        }

        @Bean
        JwtDecoder jwtDecoder() throws Exception {
            // В проде вместо ключа — issuer-uri или jwk-set-uri, ключи качаются по сети.
            var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
            if (Boolean.getBoolean("strict")) {
                OAuth2TokenValidator<Jwt> aud = new JwtClaimValidator<List<String>>("aud",
                        a -> a != null && a.contains("payments-api"));
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(ISSUER), aud));
            }
            return decoder; // Без setJwtValidator: только exp/nbf с допуском 60 секунд.
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({Api.class, Security.class})
    static class App {}

    static final HttpClient http = HttpClient.newHttpClient();
    static String base;

    static String get(String path, String bearer) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        var res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        String www = res.headers().firstValue("WWW-Authenticate").map(v -> " " + v).orElse("");
        return res.statusCode() + (res.statusCode() == 200 ? " " + res.body() : www);
    }

    static void run(boolean strict) throws Exception {
        System.setProperty("strict", Boolean.toString(strict));
        var app = new SpringApplication(App.class);
        app.setDefaultProperties(Map.of("server.port", "0", "spring.main.banner-mode", "off", "logging.level.root", "warn"));
        try (var ctx = app.run()) {
            base = "http://localhost:" + ((WebServerApplicationContext) ctx).getWebServer().getPort();
            String read = token("payments.read", "payments-api", 300, List.of("USER"));
            if (!strict) {
                System.out.println("no token          -> " + get("/api/payments", null));
                System.out.println("me                -> " + get("/api/me", read));
                System.out.println("scope read        -> " + get("/api/payments", read));
                System.out.println("scope write only  -> " + get("/api/payments", token("payments.write", "payments-api", 300, List.of())));
                System.out.println("expired 30s ago   -> " + get("/api/payments", token("payments.read", "payments-api", -30, List.of())));
                System.out.println("expired 120s ago  -> " + get("/api/payments", token("payments.read", "payments-api", -120, List.of())));
                System.out.println("tampered payload  -> " + get("/api/payments", tamper(read)));
                System.out.println("aud=ledger-api    -> " + get("/api/payments", token("payments.read", "ledger-api", 300, List.of())));
            } else {
                System.out.println("strict aud=ledger -> " + get("/api/payments", token("payments.read", "ledger-api", 300, List.of())));
                System.out.println("strict aud=pay    -> " + get("/api/payments", read));
                System.out.println("refunds as USER   -> " + get("/api/refunds", read));
                System.out.println("refunds SUPPORT   -> " + get("/api/refunds", token("payments.read", "payments-api", 300, List.of("SUPPORT"))));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        key = new RSAKeyGenerator(2048).keyID("k1").generate();
        System.out.println("== default validators");
        run(false);
        System.out.println("== issuer + audience validators");
        run(true);
    }
}
