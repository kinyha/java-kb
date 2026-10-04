import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;

// Запуск: java --add-opens java.base/java.util=ALL-UNNAMED MapsDemo.java   (JDK 21)
// --add-opens нужен только чтобы заглянуть внутрь HashSet и IdentityHashMap.
public class MapsDemo {
    // LRU-кеш: порядок доступа + вытеснение старейшего.
    static final class Lru<K, V> extends LinkedHashMap<K, V> {
        private final int max;
        Lru(int max) { super(16, 0.75f, true); this.max = max; }      // accessOrder = true
        @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > max; }
    }

    static Object field(Object o, Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== LinkedHashMap: insertion order vs access order");
        Map<String, Integer> ins = new LinkedHashMap<>();
        Lru<String, Integer> lru = new Lru<>(3);
        for (String k : List.of("RUB", "USD", "EUR")) { ins.put(k, 0); lru.put(k, 0); }
        ins.get("RUB"); lru.get("RUB");
        System.out.println("insertion order after get(RUB): " + ins.keySet());
        System.out.println("access order after get(RUB):    " + lru.keySet());
        lru.put("CNY", 0);
        System.out.println("LRU(3) after put(CNY):          " + lru.keySet() + "  (USD evicted)");

        System.out.println("== TreeMap: the rate in force on a date");
        NavigableMap<LocalDate, String> fee = new TreeMap<>();
        fee.put(LocalDate.of(2026, 1, 1), "1.5%");
        fee.put(LocalDate.of(2026, 4, 1), "1.2%");
        fee.put(LocalDate.of(2026, 9, 15), "0.9%");
        for (LocalDate d : List.of(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 9, 15), LocalDate.of(2025, 12, 31)))
            System.out.println(d + " -> floorEntry = " + fee.floorEntry(d));
        System.out.println("headMap(2026-06-01) = " + fee.headMap(LocalDate.of(2026, 6, 1)).keySet());
        NavigableMap<LocalDate, String> view = fee.tailMap(LocalDate.of(2026, 4, 1), true);
        fee.put(LocalDate.of(2026, 12, 1), "0.7%");
        System.out.println("tailMap view sees later put: " + view.keySet());

        System.out.println("== HashSet is a HashMap");
        Set<String> ids = new HashSet<>(List.of("p-1", "p-2"));
        Map<?, ?> inner = (Map<?, ?>) field(ids, HashSet.class, "map");
        System.out.println("inner " + inner.getClass().getSimpleName() + ", values: " + new HashSet<>(inner.values()).iterator().next().getClass().getSimpleName() + " PRESENT");

        System.out.println("== IdentityHashMap: == instead of equals");
        String a = new String("p-1"), b = new String("p-1");
        Map<String, Integer> hm = new HashMap<>(); Map<String, Integer> idm = new IdentityHashMap<>();
        hm.put(a, 1); hm.put(b, 2); idm.put(a, 1); idm.put(b, 2);
        System.out.println("HashMap size " + hm.size() + ", IdentityHashMap size " + idm.size());
        Object[] table = (Object[]) field(idm, IdentityHashMap.class, "table");
        System.out.println("IdentityHashMap table: one Object[" + table.length + "], keys and values side by side, linear probing");

        System.out.println("== WeakHashMap: entry disappears with its key");
        Map<Object, String> meta = new WeakHashMap<>();
        Object session = new Object();
        meta.put(session, "user-42");
        meta.put("literal-key", "pinned");                         // литерал живёт в пуле строк вечно
        session = null;
        for (int i = 0; i < 5 && meta.size() > 1; i++) { System.gc(); Thread.sleep(50); }
        System.out.println("after GC: " + meta);
        Map<Object, Object> self = new WeakHashMap<>();
        Object key = new Object();
        self.put(key, List.of(key));                                // значение держит ключ
        key = null;
        for (int i = 0; i < 5; i++) { System.gc(); Thread.sleep(50); }
        System.out.println("value references its key: size after GC = " + self.size());
    }
}
