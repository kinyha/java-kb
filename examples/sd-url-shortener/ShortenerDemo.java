import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

// Запуск: java -Xmx3g ShortenerDemo.java   (JDK 21, без зависимостей)
public class ShortenerDemo {
    static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    static String base62(long n) {
        StringBuilder sb = new StringBuilder();
        do { sb.append(ALPHABET.charAt((int) (n % 62))); n /= 62; } while (n > 0);
        return sb.reverse().toString();
    }

    public static void main(String[] args) throws Exception {
        collisions();
        counters();
        cache();
    }

    // 1. Ключ = первые k символов base62 от SHA-256 длинной ссылки.
    static void collisions() throws Exception {
        System.out.println("== hash prefix as key: collisions among 10,000,000 distinct URLs");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        int n = 10_000_000;
        for (int k : new int[] {6, 7}) {
            Set<String> seen = new HashSet<>(n * 2);
            long space = (long) Math.pow(62, k);
            int collisions = 0;
            for (int i = 0; i < n; i++) {
                byte[] d = sha.digest(("https://shop.example/item/" + i + "?ref=mail").getBytes(StandardCharsets.UTF_8));
                long h = 0;
                for (int b = 0; b < 8; b++) h = (h << 8) | (d[b] & 0xff);
                String key = base62(Long.remainderUnsigned(h, space));
                if (!seen.add(key)) collisions++;
            }
            System.out.printf("   %d chars (62^%d = %.1e keys): %d collisions, birthday estimate %.0f%n",
                    k, k, (double) space, collisions, (double) n * n / (2.0 * space));
        }
    }

    // 2. Счётчик: уникален без проверок, но коды идут подряд. Перестановка (сеть Фейстеля) прячет порядок.
    static void counters() {
        System.out.println("== counter-based keys");
        List<String> plain = new ArrayList<>(), mixed = new ArrayList<>();
        for (long id = 1_000_000; id < 1_000_004; id++) { plain.add(base62(id)); mixed.add(base62(feistel(id))); }
        System.out.println("   counter:  " + String.join(" ", plain));
        System.out.println("   permuted: " + String.join(" ", mixed));
        Set<Long> out = new HashSet<>();
        for (long id = 0; id < 1_000_000; id++) out.add(feistel(id));
        System.out.println("   permutation of 1,000,000 ids: " + out.size() + " distinct, max code length "
                + out.stream().mapToLong(x -> x).map(x -> base62(x).length()).max().orElseThrow());
    }

    // Сеть Фейстеля на 40 битах: обратимая перестановка, поэтому разные id дают разные коды.
    // 2^40 = 1.1e12 < 62^7 = 3.5e12 — код не длиннее 7 символов. Ключи раундов — секрет сервиса.
    static long feistel(long x) {
        long l = x >>> 20, r = x & 0xFFFFF;
        long[] keys = {0x5bd1e995L, 0x1b873593L, 0x2c1b3c6dL, 0x297a2d39L};
        for (long k : keys) {
            long f = ((r * 0x9E3779B1L) ^ k) & 0xFFFFF;
            long t = l ^ f; l = r; r = t;
        }
        return (l << 20) | r;
    }

    // 3. Чтения подчиняются закону Ципфа: немногие ссылки получают почти все переходы.
    static void cache() {
        System.out.println("== LRU cache hit rate, 10,000,000 links, 20,000,000 Zipf(1.0) reads");
        int links = 10_000_000;
        double[] cdf = new double[links];
        double sum = 0;
        for (int i = 0; i < links; i++) { sum += 1.0 / (i + 1); cdf[i] = sum; }
        Random rnd = new Random(42);
        int reads = 20_000_000;
        int[] stream = new int[reads];
        for (int i = 0; i < reads; i++) {
            int idx = Arrays.binarySearch(cdf, rnd.nextDouble() * sum);
            stream[i] = idx >= 0 ? idx : -idx - 1;
        }
        for (double share : new double[] {0.001, 0.01, 0.1}) {
            int cap = (int) (links * share);
            var lru = new LinkedHashMap<Integer, Boolean>(cap * 2, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> e) { return size() > cap; }
            };
            long hits = 0;
            for (int key : stream) { if (lru.get(key) != null) hits++; else lru.put(key, Boolean.TRUE); }
            System.out.printf("   cache %5.1f%% of links (%,9d): hit rate %.1f%%%n", share * 100, cap, hits * 100.0 / reads);
        }
    }
}
