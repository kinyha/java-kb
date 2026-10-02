import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Запуск: java OAuthFlowDemo.java   (JDK 21)
// Сервер авторизации в одном процессе: методы authorize() и token() — его эндпоинты.
// Проверки повторяют RFC 6749, RFC 7636 (PKCE) и RFC 9700.
public class OAuthFlowDemo {
    record Client(String id, String secret, Set<String> redirects, Set<String> scopes) {}
    record Code(String client, String redirect, String scope, String challenge, String user) {}

    static final Map<String, Client> clients = Map.of(
            "shop-app", new Client("shop-app", null, Set.of("https://app.pay.example/cb"), Set.of("payments:read")), // Публичный: SPA, мобильное.
            "payouts-job", new Client("payouts-job", "j0b-s3cret", Set.of(), Set.of("payouts:write")));          // Конфиденциальный: сервер.
    static final Map<String, Code> codes = new HashMap<>();
    static final SecureRandom random = new SecureRandom();
    static int n;

    // GET /authorize — пользователь уже вошёл и согласился; сервер отвечает редиректом.
    static String authorize(String clientId, String redirect, String scope, String state, String challenge, String user) {
        Client c = clients.get(clientId);
        if (c == null || !c.redirects().contains(redirect))           // Точное совпадение, без префиксов.
            return "400 invalid redirect_uri, no redirect";
        if (!c.scopes().contains(scope)) return redirect + "?error=invalid_scope&state=" + state;
        if (challenge == null) return redirect + "?error=invalid_request&error_description=pkce_required&state=" + state;
        String code = "c-" + (++n);
        codes.put(code, new Code(clientId, redirect, scope, challenge, user));
        return redirect + "?code=" + code + "&state=" + state;
    }

    // POST /token, grant_type=authorization_code.
    static String tokenByCode(String clientId, String code, String redirect, String verifier) {
        Code c = codes.remove(code);                                   // Код одноразовый.
        if (c == null) return "400 invalid_grant (unknown or used code)";
        if (!c.client().equals(clientId) || !c.redirect().equals(redirect)) return "400 invalid_grant (client or redirect)";
        if (verifier == null || !s256(verifier).equals(c.challenge())) return "400 invalid_grant (PKCE verifier)";
        return "200 access_token(sub=" + c.user() + ", aud=payments, scope=" + c.scope() + ", exp=5m) + refresh_token";
    }

    // POST /token, grant_type=client_credentials: пользователя нет, клиент действует от себя.
    static String tokenByClientCredentials(String clientId, String secret, String scope) {
        Client c = clients.get(clientId);
        if (c == null || c.secret() == null || !MessageDigest.isEqual(c.secret().getBytes(), secret.getBytes()))
            return "401 invalid_client";
        if (!c.scopes().contains(scope)) return "400 invalid_scope";
        return "200 access_token(sub=" + clientId + ", scope=" + scope + ", exp=5m), no refresh_token";
    }

    public static void main(String[] args) {
        System.out.println("== authorization code + PKCE");
        String verifier = random(32);                                  // Знает только приложение.
        String state = random(16);                                     // Привязывает ответ к этому браузеру.
        String challenge = s256(verifier);
        System.out.println("verifier  = " + verifier.substring(0, 12) + "... (stays in the app)");
        System.out.println("challenge = " + challenge.substring(0, 12) + "... (sent to /authorize)");
        String redirect = authorize("shop-app", "https://app.pay.example/cb", "payments:read", state, challenge, "anna");
        System.out.println("302 -> " + redirect.replace(state, "<state>"));
        String code = redirect.replaceAll(".*code=([^&]+).*", "$1");
        String returnedState = redirect.replaceAll(".*state=([^&]+).*", "$1");
        System.out.println("app checks state: " + (returnedState.equals(state) ? "match" : "MISMATCH"));
        System.out.println("attacker with stolen code -> " + tokenByCode("shop-app", code, "https://app.pay.example/cb", random(32)));

        String code2 = authorize("shop-app", "https://app.pay.example/cb", "payments:read", state, challenge, "anna")
                .replaceAll(".*code=([^&]+).*", "$1");
        System.out.println("app with verifier         -> " + tokenByCode("shop-app", code2, "https://app.pay.example/cb", verifier));
        System.out.println("same code again           -> " + tokenByCode("shop-app", code2, "https://app.pay.example/cb", verifier));

        System.out.println("== authorize checks");
        System.out.println("redirect to evil.example  -> " + authorize("shop-app", "https://evil.example/cb", "payments:read", state, challenge, "anna"));
        System.out.println("redirect with extra path  -> " + authorize("shop-app", "https://app.pay.example/cb/../steal", "payments:read", state, challenge, "anna"));
        System.out.println("scope payouts:write       -> " + authorize("shop-app", "https://app.pay.example/cb", "payouts:write", "s1", challenge, "anna"));
        System.out.println("without PKCE              -> " + authorize("shop-app", "https://app.pay.example/cb", "payments:read", "s2", null, "anna"));

        System.out.println("== client credentials");
        System.out.println("payouts-job               -> " + tokenByClientCredentials("payouts-job", "j0b-s3cret", "payouts:write"));
        System.out.println("wrong secret              -> " + tokenByClientCredentials("payouts-job", "guess", "payouts:write"));
        System.out.println("public client shop-app    -> " + tokenByClientCredentials("shop-app", "", "payments:read"));
    }

    static String random(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // code_challenge = BASE64URL(SHA-256(ASCII(code_verifier))), RFC 7636.
    static String s256(String verifier) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
