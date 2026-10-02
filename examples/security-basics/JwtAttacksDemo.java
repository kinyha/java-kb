import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Запуск: java JwtAttacksDemo.java   (JDK 21, без библиотек)
// JWT собирается вручную, чтобы были видны байты, которые подписываются.
public class JwtAttacksDemo {
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder D64 = Base64.getUrlDecoder();

    public static void main(String[] args) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair issuer = g.generateKeyPair();                  // Ключ сервера авторизации.
        byte[] publicPem = pem((RSAPublicKey) issuer.getPublic()); // Публичный ключ знают все.
        long now = Instant.now().getEpochSecond();

        String good = rs256(issuer, "{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
                "{\"iss\":\"https://id.pay.example\",\"sub\":\"anna\",\"aud\":\"payments\",\"role\":\"user\",\"exp\":" + (now + 300) + "}");
        System.out.println("== token");
        String[] p = good.split("\\.");
        System.out.println("header:  " + new String(D64.decode(p[0])));
        System.out.println("payload: " + new String(D64.decode(p[1])));
        System.out.println("length:  " + good.length() + " chars");

        String adminPayload = "{\"iss\":\"https://id.pay.example\",\"sub\":\"anna\",\"aud\":\"payments\",\"role\":\"admin\",\"exp\":" + (now + 300) + "}";
        // Атака 1: alg=none и пустая подпись.
        String none = b64("{\"alg\":\"none\"}") + "." + b64(adminPayload) + ".";
        // Атака 2: HS256, где «секретом» служит публичный ключ RSA.
        String hsInput = b64("{\"alg\":\"HS256\"}") + "." + b64(adminPayload);
        String confused = hsInput + "." + B64.encodeToString(hmac(publicPem, hsInput));
        // Чужой токен: подписан тем же издателем, но для другого сервиса.
        String otherAud = rs256(issuer, "{\"alg\":\"RS256\"}",
                "{\"iss\":\"https://id.pay.example\",\"sub\":\"anna\",\"aud\":\"reports\",\"role\":\"user\",\"exp\":" + (now + 300) + "}");
        String expired = rs256(issuer, "{\"alg\":\"RS256\"}",
                "{\"iss\":\"https://id.pay.example\",\"sub\":\"anna\",\"aud\":\"payments\",\"role\":\"user\",\"exp\":" + (now - 120) + "}");

        Map<String, String> cases = new java.util.LinkedHashMap<>();
        cases.put("valid", good);
        cases.put("alg=none", none);
        cases.put("HS256 with public key", confused);
        cases.put("aud=reports", otherAud);
        cases.put("expired 2 min ago", expired);

        System.out.println("== naive verifier: trusts header alg, checks only signature");
        for (var c : cases.entrySet()) System.out.printf("%-22s -> %s%n", c.getKey(), naive(c.getValue(), issuer, publicPem));
        System.out.println("== strict verifier: RS256 only, iss, aud, exp with 60 s skew");
        for (var c : cases.entrySet()) System.out.printf("%-22s -> %s%n", c.getKey(), strict(c.getValue(), issuer, now));

        refreshRotation();
    }

    // Наивная проверка: алгоритм берёт из заголовка токена.
    static String naive(String jwt, KeyPair issuer, byte[] publicPem) throws Exception {
        String[] p = jwt.split("\\.", -1);
        String alg = claim(new String(D64.decode(p[0])), "alg");
        String input = p[0] + "." + p[1];
        boolean ok = switch (alg) {
            case "none" -> true;
            case "HS256" -> MessageDigest.isEqual(hmac(publicPem, input), D64.decode(p[2])); // «Ключ проверки» — тот же.
            case "RS256" -> rsVerify(issuer, input, p[2]);
            default -> false;
        };
        return ok ? "accepted, role=" + claim(new String(D64.decode(p[1])), "role") : "rejected";
    }

    // Строгая проверка: алгоритм задан сервером, claims проверяются явно.
    static String strict(String jwt, KeyPair issuer, long now) throws Exception {
        String[] p = jwt.split("\\.", -1);
        if (!"RS256".equals(claim(new String(D64.decode(p[0])), "alg"))) return "rejected: alg";
        if (!rsVerify(issuer, p[0] + "." + p[1], p[2])) return "rejected: signature";
        String body = new String(D64.decode(p[1]));
        if (!"https://id.pay.example".equals(claim(body, "iss"))) return "rejected: iss";
        if (!"payments".equals(claim(body, "aud"))) return "rejected: aud";
        if (Long.parseLong(claim(body, "exp")) + 60 < now) return "rejected: exp";
        return "accepted, role=" + claim(body, "role");
    }

    // ---------- refresh-токены: ротация и обнаружение повторного использования ----------
    record Refresh(String family, boolean used) {}
    static final Map<String, Refresh> refreshStore = new HashMap<>();
    static final java.util.Set<String> revokedFamilies = new java.util.HashSet<>();
    static int counter;

    static String issueRefresh(String family) {
        String t = "rt-" + (++counter);
        refreshStore.put(t, new Refresh(family, false));
        return t;
    }

    static String useRefresh(String t) {
        Refresh r = refreshStore.get(t);
        if (r == null || revokedFamilies.contains(r.family())) return "401";
        if (r.used()) {                                   // Второй раз — значит, токен украден.
            revokedFamilies.add(r.family());
            return "401 reuse detected, family " + r.family() + " revoked";
        }
        refreshStore.put(t, new Refresh(r.family(), true));
        return "200 new access + " + issueRefresh(r.family());
    }

    static void refreshRotation() {
        System.out.println("== refresh rotation");
        String rt1 = issueRefresh("f-anna");
        System.out.println("thief  uses " + rt1 + "   -> " + useRefresh(rt1));  // Украл и успел первым.
        System.out.println("client uses " + rt1 + "   -> " + useRefresh(rt1));  // Настоящий клиент приходит со старым.
        System.out.println("thief  uses rt-2   -> " + useRefresh("rt-2"));
    }

    // ---------- утилиты ----------
    static String b64(String s) { return B64.encodeToString(s.getBytes(StandardCharsets.UTF_8)); }

    static String rs256(KeyPair k, String header, String payload) throws Exception {
        String input = b64(header) + "." + b64(payload);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(k.getPrivate());
        s.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + B64.encodeToString(s.sign());
    }

    static boolean rsVerify(KeyPair k, String input, String sig) throws Exception {
        if (sig.isEmpty()) return false;
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initVerify(k.getPublic());
        s.update(input.getBytes(StandardCharsets.US_ASCII));
        return s.verify(D64.decode(sig));
    }

    static byte[] hmac(byte[] key, String input) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(input.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] pem(RSAPublicKey k) {
        return ("-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(k.getEncoded())
                + "\n-----END PUBLIC KEY-----\n").getBytes(StandardCharsets.US_ASCII);
    }

    static String claim(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"?([^\",}]*)").matcher(json);
        return m.find() ? m.group(1) : "";
    }
}
