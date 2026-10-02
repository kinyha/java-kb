import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.HexFormat;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

// Запуск: java CryptoDemo.java   (JDK 21, только стандартные провайдеры)
public class CryptoDemo {
    static final SecureRandom random = new SecureRandom();
    static final HexFormat HEX = HexFormat.of();

    public static void main(String[] args) throws Exception {
        passwordHashing();
        salt();
        ecbVsGcm();
        gcmTamper();
        gcmNonceReuse();
        signatures();
    }

    // ---------- Быстрый хеш против медленного ----------
    static void passwordHashing() throws Exception {
        System.out.println("== password hashing cost (one core)");
        byte[] pwd = "qwerty123".getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < 200_000; i++) sha.digest(pwd);             // Прогрев.
        long t = System.nanoTime();
        int n = 1_000_000;
        for (int i = 0; i < n; i++) sha.digest(pwd);
        double shaPerSec = n / ((System.nanoTime() - t) / 1e9);
        System.out.printf("SHA-256:                ~%.1f million guesses/s%n", shaPerSec / 1e6);

        SecretKeyFactory pbkdf2 = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] salt = bytes(16);
        pbkdf2.generateSecret(new PBEKeySpec("qwerty123".toCharArray(), salt, 600_000, 256)); // Прогрев.
        t = System.nanoTime();
        for (int i = 0; i < 5; i++) pbkdf2.generateSecret(new PBEKeySpec("qwerty123".toCharArray(), salt, 600_000, 256));
        double ms = (System.nanoTime() - t) / 1e6 / 5;
        System.out.printf("PBKDF2-SHA256 x600000:  ~%.0f ms per guess (~%.0f guesses/s)%n", ms, 1000 / ms);
    }

    // ---------- Соль ----------
    static void salt() throws Exception {
        System.out.println("== salt");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        String a = HEX.formatHex(sha.digest("qwerty123".getBytes())).substring(0, 16);
        String b = HEX.formatHex(sha.digest("qwerty123".getBytes())).substring(0, 16);
        System.out.println("anna  sha256(pwd)        = " + a + "...");
        System.out.println("boris sha256(pwd)        = " + b + "...  same password visible");
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        for (String user : new String[] {"anna ", "boris"}) {
            byte[] s = bytes(16);
            byte[] h = f.generateSecret(new PBEKeySpec("qwerty123".toCharArray(), s, 600_000, 256)).getEncoded();
            System.out.println(user + " pbkdf2(salt, pwd)  = " + HEX.formatHex(h).substring(0, 16) + "...  salt " + HEX.formatHex(s).substring(0, 8) + "...");
        }
    }

    // ---------- ECB раскрывает повторы ----------
    static void ecbVsGcm() throws Exception {
        System.out.println("== ECB vs GCM, three equal 16-byte blocks");
        SecretKey key = aesKey();
        byte[] plain = "status=CAPTURED;status=CAPTURED;status=CAPTURED;".getBytes(StandardCharsets.US_ASCII);
        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, key);
        System.out.println("ECB: " + blocks(ecb.doFinal(plain)));
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, bytes(12)));
        System.out.println("GCM: " + blocks(gcm.doFinal(plain)));
    }

    // ---------- GCM проверяет целостность ----------
    static void gcmTamper() throws Exception {
        System.out.println("== GCM tamper");
        SecretKey key = aesKey();
        byte[] iv = bytes(12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal("amount=500".getBytes());
        ct[7] ^= 0x01;                                                     // Один бит в шифртексте.
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        try {
            c.doFinal(ct);
        } catch (AEADBadTagException e) {
            System.out.println("flipped 1 bit -> " + e.getClass().getSimpleName());
        }
    }

    // ---------- Повтор nonce в GCM ----------
    static void gcmNonceReuse() throws Exception {
        System.out.println("== GCM nonce reuse");
        SecretKey key = aesKey();
        byte[] iv = bytes(12);                                            // Один и тот же nonce дважды.
        byte[] p1 = "card=4111111111111111".getBytes();
        byte[] p2 = "card=5500005555555559".getBytes();
        byte[] c1 = gcm(key, iv, p1), c2 = gcm(key, iv, p2);
        byte[] recovered = new byte[p2.length];
        for (int i = 0; i < p2.length; i++) recovered[i] = (byte) (c1[i] ^ c2[i] ^ p1[i]); // Ключ не нужен.
        System.out.println("attacker knows p1, recovers p2 = " + new String(recovered));
    }

    // ---------- Подпись ----------
    static void signatures() throws Exception {
        System.out.println("== Ed25519 signature");
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] msg = "payout p-1 amount=500 to m-7".getBytes();
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(kp.getPrivate());
        s.update(msg);
        byte[] sig = s.sign();
        System.out.println("signature bytes = " + sig.length + ", public key (X.509) bytes = " + kp.getPublic().getEncoded().length);
        System.out.println("verify original = " + verify(kp, msg, sig));
        System.out.println("verify amount=5000 = " + verify(kp, "payout p-1 amount=5000 to m-7".getBytes(), sig));
    }

    static boolean verify(KeyPair kp, byte[] msg, byte[] sig) throws Exception {
        Signature v = Signature.getInstance("Ed25519");
        v.initVerify(kp.getPublic());
        v.update(msg);
        return v.verify(sig);
    }

    // ---------- утилиты ----------
    static byte[] gcm(SecretKey key, byte[] iv, byte[] p) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return c.doFinal(p);
    }

    static SecretKey aesKey() throws Exception {
        KeyGenerator g = KeyGenerator.getInstance("AES");
        g.init(256);
        return g.generateKey();
    }

    static byte[] bytes(int n) { byte[] b = new byte[n]; random.nextBytes(b); return b; }

    static String blocks(byte[] ct) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 16 <= Math.min(ct.length, 48); i += 16) sb.append(HEX.formatHex(ct, i, i + 4)).append("… ");
        return sb.toString().trim();
    }
}
