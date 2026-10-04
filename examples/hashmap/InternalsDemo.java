import java.lang.reflect.Field;
import java.util.*;

// Запуск: java --add-opens java.base/java.util=ALL-UNNAMED InternalsDemo.java   (JDK 21)
// --add-opens нужен, чтобы заглянуть в поле table и в классы узлов.
public class InternalsDemo {
    static final Field TABLE, THRESHOLD;
    static {
        try {
            TABLE = HashMap.class.getDeclaredField("table"); TABLE.setAccessible(true);
            THRESHOLD = HashMap.class.getDeclaredField("threshold"); THRESHOLD.setAccessible(true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static Object[] table(HashMap<?, ?> m) { try { return (Object[]) TABLE.get(m); } catch (Exception e) { throw new RuntimeException(e); } }
    static int threshold(HashMap<?, ?> m) { try { return THRESHOLD.getInt(m); } catch (Exception e) { throw new RuntimeException(e); } }
    static int spread(Object k) { int h; return k == null ? 0 : (h = k.hashCode()) ^ (h >>> 16); }   // как HashMap.hash

    // Ключ с намеренно плохим hashCode: все в одной корзине.
    record BadKey(int id) implements Comparable<BadKey> {
        @Override public int hashCode() { return 42; }
        @Override public int compareTo(BadKey o) { return Integer.compare(id, o.id); }
    }
    static final class BadKeyNoCompare {
        final int id; BadKeyNoCompare(int id) { this.id = id; }
        @Override public int hashCode() { return 42; }
        @Override public boolean equals(Object o) { return o instanceof BadKeyNoCompare b && b.id == id; }
    }

    public static void main(String[] args) {
        System.out.println("== where keys land in a table of 16");
        for (String k : List.of("p-1", "p-2", "p-17", "RUB", "USD"))
            System.out.printf("%-5s hashCode %10d  spread %10d  index %2d%n", k, k.hashCode(), spread(k), spread(k) & 15);
        System.out.println("null  -> index 0");

        System.out.println("== why the high bits are mixed in");
        int same = 0, spreadSame = 0;
        Set<Integer> raw = new HashSet<>(), mixed = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            int h = i << 16;                                   // хеши отличаются только старшими битами
            raw.add(h & 15); mixed.add((h ^ (h >>> 16)) & 15);
        }
        System.out.println("1000 hashes i<<16: distinct buckets without mixing = " + raw.size() + ", with mixing = " + mixed.size());

        System.out.println("== resize: table and threshold");
        HashMap<String, Integer> m = new HashMap<>();
        System.out.println("new HashMap(): table = " + (table(m) == null ? "null" : table(m).length));
        int lastLen = 0;
        for (int i = 1; i <= 100; i++) {
            m.put("p-" + i, i);
            int len = table(m).length;
            if (len != lastLen) { System.out.printf("after put #%-3d table %3d, threshold %3d%n", i, len, threshold(m)); lastLen = len; }
        }
        System.out.println("new HashMap<>(100) holds 100 without resize: " + holdsWithoutResize(new HashMap<>(100), 100));
        System.out.println("HashMap.newHashMap(100) holds 100 without resize: " + holdsWithoutResize(HashMap.newHashMap(100), 100));
        HashMap<String, Integer> req = new HashMap<>(100);
        req.put("x", 1);
        System.out.println("new HashMap<>(100) table = " + table(req).length + ", newHashMap(100) table = " + tableAfterPut(HashMap.newHashMap(100)));

        System.out.println("== split on resize: index i stays or moves to i + oldCap");
        HashMap<Integer, String> split = new HashMap<>();
        int[] keys = {5, 21, 37, 53};                              // в таблице 16 все в корзине 5
        for (int k : keys) split.put(k, "v");
        for (int i = 0; i < 9; i++) split.put(100 + i, "fill");   // 13 элементов → рост до 32
        for (int k : keys) System.out.printf("key %2d: index in 16 = %d, in 32 = %2d (hash & 16 = %2d)%n", k, k & 15, k & 31, k & 16);

        System.out.println("== collisions: list, then tree");
        HashMap<BadKey, Integer> bad = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            bad.put(new BadKey(i), i);
            Object bin = table(bad)[42 & (table(bad).length - 1)];
            if (i == 7 || i == 8 || i == 9 || i == 10)
                System.out.printf("%2d keys in one bin: table %3d, bin node %s%n", i + 1, table(bad).length, bin.getClass().getSimpleName());
        }

        System.out.println("== get() cost with 50 000 keys in one bin");
        int n = 50_000;
        HashMap<BadKey, Integer> cmp = new HashMap<>(); HashMap<BadKeyNoCompare, Integer> nocmp = new HashMap<>();
        for (int i = 0; i < n; i++) { cmp.put(new BadKey(i), i); nocmp.put(new BadKeyNoCompare(i), i); }
        for (int r = 0; r < 3; r++) {
            long t0 = System.nanoTime(); long s = 0;
            for (int i = 0; i < n; i += 10) s += cmp.get(new BadKey(i));
            long t1 = System.nanoTime();
            for (int i = 0; i < n; i += 10) s += nocmp.get(new BadKeyNoCompare(i));
            long t2 = System.nanoTime();
            if (r == 2) System.out.printf("5000 gets: Comparable keys %.1f ms, non-Comparable keys %.1f ms (sum %d)%n", (t1 - t0) / 1e6, (t2 - t1) / 1e6, s);
        }
    }
    static boolean holdsWithoutResize(HashMap<Integer, Integer> m, int n) {
        m.put(-1, -1); int len = table(m).length; m.remove(-1);
        for (int i = 0; i < n; i++) m.put(i, i);
        return table(m).length == len;
    }
    static int tableAfterPut(HashMap<String, Integer> m) { m.put("x", 1); return table(m).length; }
}
