import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

// Запуск: java HashRingDemo.java   (JDK 21, без зависимостей)
// 1 000 000 ключей карт раскладываем по узлам кеша тремя способами и смотрим,
// сколько ключей переезжает при добавлении узла и насколько ровно распределена нагрузка.
public class HashRingDemo {
    static final int KEYS = 1_000_000;

    // Хеш: первые 8 байт MD5. Нужна хорошая перемешивающая функция; String.hashCode для кольца плох.
    static long hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xff);
            return h;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    interface Placement { String nodeFor(long keyHash); }

    // hash % N: узел — остаток от деления.
    static Placement modulo(List<String> nodes) {
        return h -> nodes.get((int) Math.floorMod(h, (long) nodes.size()));
    }

    // Кольцо: каждый узел занимает vnodes точек; ключ идёт к первой точке по часовой стрелке.
    static Placement ring(List<String> nodes, int vnodes) {
        TreeMap<Long, String> ring = new TreeMap<>();
        for (String n : nodes) for (int v = 0; v < vnodes; v++) ring.put(hash(n + "#" + v), n);
        return h -> {
            SortedMap<Long, String> tail = ring.tailMap(h);
            return tail.isEmpty() ? ring.firstEntry().getValue() : tail.get(tail.firstKey());
        };
    }

    public static void main(String[] args) {
        long[] keys = new long[KEYS];
        for (int i = 0; i < KEYS; i++) keys[i] = hash("card-" + i);
        List<String> four = List.of("cache-1", "cache-2", "cache-3", "cache-4");
        List<String> five = List.of("cache-1", "cache-2", "cache-3", "cache-4", "cache-5");

        System.out.println("== adding cache-5 to 4 nodes: share of 1,000,000 keys that move");
        report("hash % N", keys, modulo(four), modulo(five));
        for (int v : new int[] {1, 10, 100, 1000})
            report("ring, " + v + " vnodes", keys, ring(four, v), ring(five, v));

        System.out.println("== cache-3 fails: who takes its keys");
        List<String> without3 = List.of("cache-1", "cache-2", "cache-4", "cache-5");
        for (int v : new int[] {1, 100}) {
            Placement before = ring(five, v), after = ring(without3, v);
            Map<String, Integer> receivers = new TreeMap<>();
            for (long k : keys) if (before.nodeFor(k).equals("cache-3")) receivers.merge(after.nodeFor(k), 1, Integer::sum);
            System.out.println("   ring, " + v + " vnodes: " + receivers);
        }
    }

    static void report(String name, long[] keys, Placement before, Placement after) {
        int moved = 0;
        Map<String, Integer> load = new HashMap<>();
        for (long k : keys) {
            String a = before.nodeFor(k), b = after.nodeFor(k);
            if (!a.equals(b)) moved++;
            load.merge(b, 1, Integer::sum);
        }
        int max = load.values().stream().max(Integer::compare).orElseThrow();
        int min = load.values().stream().min(Integer::compare).orElseThrow();
        double avg = keys.length / 5.0;
        System.out.printf("   %-20s moved %5.1f%%   load on 5 nodes: min %4.0f%%, max %4.0f%% of average%n",
                name, moved * 100.0 / keys.length, min * 100 / avg, max * 100 / avg);
    }
}
