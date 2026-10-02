import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Запуск: java AuthDemo.java   (JDK 21)
public class AuthDemo {
    static final SecureRandom random = new SecureRandom();

    public static void main(String[] args) throws Exception {
        basic();
        sessionsVsTokens();
        apiKeys();
        signedRequests();
    }

    // ---------- Basic: base64 — это не шифрование ----------
    static void basic() {
        System.out.println("== basic");
        String header = "Basic " + Base64.getEncoder().encodeToString("shop-7:s3cret!".getBytes(StandardCharsets.UTF_8));
        System.out.println("Authorization: " + header);
        System.out.println("decoded: " + new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8));
    }

    // ---------- Сессия на сервере против самодостаточного токена ----------
    static final Map<String, String> sessions = new ConcurrentHashMap<>(); // sessionId -> user
    static final byte[] tokenKey = bytes(32);

    static void sessionsVsTokens() throws Exception {
        System.out.println("== session vs self-contained token");
        String sid = token(32);
        sessions.put(sid, "anna");
        long exp = Instant.now().getEpochSecond() + 900;                  // Токен живёт 15 минут.
        String body = "anna|" + exp;
        String selfContained = body + "|" + hmacHex(tokenKey, body);

        System.out.println("before logout: session=" + sessionUser(sid) + ", token=" + tokenUser(selfContained));
        sessions.remove(sid);                                               // Выход или блокировка.
        System.out.println("after logout:  session=" + sessionUser(sid) + ", token=" + tokenUser(selfContained));
    }

    static String sessionUser(String sid) { return sessions.getOrDefault(sid, "401"); }

    static String tokenUser(String t) throws Exception {
        String[] p = t.split("\\|");
        boolean ok = MessageDigest.isEqual(hmacHex(tokenKey, p[0] + "|" + p[1]).getBytes(), p[2].getBytes());
        return ok && Long.parseLong(p[1]) > Instant.now().getEpochSecond() ? p[0] : "401";
    }

    // ---------- API-ключ: показать один раз, хранить хеш ----------
    record StoredKey(String owner, String sha256) {}
    static final Map<String, StoredKey> keysByPrefix = new ConcurrentHashMap<>();

    static void apiKeys() throws Exception {
        System.out.println("== api keys");
        String prefix = "pk_live_" + token(4);
        String secret = token(24);
        String fullKey = prefix + "." + secret;                           // Отдаётся клиенту один раз.
        keysByPrefix.put(prefix, new StoredKey("shop-7", sha256Hex(fullKey)));
        System.out.println("issued:  " + fullKey);
        System.out.println("stored:  " + prefix + " -> " + keysByPrefix.get(prefix));
        System.out.println("check valid:   " + checkKey(fullKey));
        System.out.println("check guessed: " + checkKey(prefix + "." + token(24)));
    }

    static String checkKey(String presented) throws Exception {
        String prefix = presented.substring(0, presented.indexOf('.'));
        StoredKey k = keysByPrefix.get(prefix);                           // Поиск по префиксу — по индексу.
        if (k == null) return "401";
        boolean ok = MessageDigest.isEqual(sha256Hex(presented).getBytes(), k.sha256().getBytes()); // Без раннего выхода.
        return ok ? k.owner() : "401";
    }

    // ---------- Подпись запроса HMAC: секрет не передаётся по сети ----------
    static final byte[] shopSecret = bytes(32);

    static void signedRequests() throws Exception {
        System.out.println("== signed requests");
        long now = Instant.now().getEpochSecond();
        String body = "{\"amount\":500}";
        String sig = sign("POST", "/payments", now, body);
        System.out.println("signature: " + sig.substring(0, 16) + "...");
        System.out.println("original:        " + verify("POST", "/payments", now, body, sig, now));
        System.out.println("amount changed:  " + verify("POST", "/payments", now, "{\"amount\":50000}", sig, now));
        System.out.println("replayed +10min: " + verify("POST", "/payments", now, body, sig, now + 600));
    }

    static String sign(String method, String path, long ts, String body) throws Exception {
        return hmacHex(shopSecret, method + "\n" + path + "\n" + ts + "\n" + sha256Hex(body));
    }

    static String verify(String method, String path, long ts, String body, String sig, long serverNow) throws Exception {
        if (Math.abs(serverNow - ts) > 300) return "401 stale timestamp";      // Окно 5 минут против повтора.
        boolean ok = MessageDigest.isEqual(sign(method, path, ts, body).getBytes(), sig.getBytes());
        return ok ? "200" : "401 bad signature";
    }

    // ---------- утилиты ----------
    static byte[] bytes(int n) { byte[] b = new byte[n]; random.nextBytes(b); return b; }
    static String token(int n) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(n)); }

    static String sha256Hex(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    static String hmacHex(byte[] key, String s) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(s.getBytes(StandardCharsets.UTF_8)));
    }
}
