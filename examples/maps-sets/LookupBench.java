import java.util.*;

// Запуск: java -Xmx2g LookupBench.java   (JDK 21). Однопоточный замер, лучший из пяти прогонов после прогрева.
public class LookupBench {
    public static void main(String[] args) {
        int n = 1_000_000;
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < n; i++) keys.add("acc-" + i);
        Collections.shuffle(keys, new Random(1));
        Map<String, Integer> hash = new HashMap<>(), linked = new LinkedHashMap<>(), tree = new TreeMap<>();
        for (String k : keys) { hash.put(k, 1); linked.put(k, 1); tree.put(k, 1); }
        for (var e : List.of(Map.entry("HashMap", hash), Map.entry("LinkedHashMap", linked), Map.entry("TreeMap", tree))) {
            double best = Double.MAX_VALUE; long s = 0;
            for (int r = 0; r < 5; r++) {
                long t0 = System.nanoTime();
                for (String k : keys) s += e.getValue().get(k);
                best = Math.min(best, (System.nanoTime() - t0) / 1e6);
            }
            System.out.printf("%-14s 1M get: %6.1f ms (%d)%n", e.getKey(), best, s);
        }
    }
}
