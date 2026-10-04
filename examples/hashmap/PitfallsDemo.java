import java.util.*;
import java.util.concurrent.*;

// Запуск: java PitfallsDemo.java   (JDK 21, без зависимостей)
public class PitfallsDemo {
    // equals переопределён, hashCode — нет.
    static final class Account {
        final String iban;
        Account(String iban) { this.iban = iban; }
        @Override public boolean equals(Object o) { return o instanceof Account a && a.iban.equals(iban); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== equals without hashCode");
        Map<Account, Long> balances = new HashMap<>();
        balances.put(new Account("DE01"), 100L);
        balances.put(new Account("DE01"), 200L);                      // «тот же» счёт
        System.out.println("size = " + balances.size() + ", get = " + balances.get(new Account("DE01")));

        System.out.println("== iteration order is not stable");
        Map<String, Integer> m = new HashMap<>();
        for (String k : List.of("p-9", "p-10", "p-11", "p-12")) m.put(k, 0);
        System.out.println("4 keys:        " + m.keySet());
        for (int i = 100; i < 120; i++) m.put("x-" + i, 0);
        for (int i = 100; i < 120; i++) m.remove("x-" + i);
        System.out.println("after a resize: " + m.keySet());

        System.out.println("== counting: merge instead of get + put");
        Map<String, Integer> byCurrency = new HashMap<>();
        for (String c : List.of("RUB", "USD", "RUB", "EUR", "RUB")) byCurrency.merge(c, 1, Integer::sum);
        System.out.println("counts = " + new TreeMap<>(byCurrency));

        System.out.println("== HashMap shared by threads");
        for (int round = 0; round < 3; round++) {
            Map<Integer, Integer> shared = new HashMap<>();
            Map<Integer, Integer> chm = new ConcurrentHashMap<>();
            int threads = 8, per = 50_000;
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                for (int t = 0; t < threads; t++) {
                    int base = t * per;
                    pool.execute(() -> { for (int i = 0; i < per; i++) { shared.put(base + i, i); chm.put(base + i, i); } });
                }
            }
            System.out.printf("expected %d: HashMap %d, ConcurrentHashMap %d%n", threads * per, shared.size(), chm.size());
        }
    }
}
