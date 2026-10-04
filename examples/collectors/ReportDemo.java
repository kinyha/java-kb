import java.util.*;
import java.util.stream.*;
import static java.util.stream.Collectors.*;

// Запуск: java ReportDemo.java   (JDK 21, без зависимостей)
// Типичные отчёты: сортировка карты по значению, топ-N в группе, частоты.
public class ReportDemo {
    record Payment(String id, String client, String currency, long amount) {}
    static final List<Payment> BATCH = List.of(
            new Payment("p-1", "acme", "RUB", 500), new Payment("p-2", "acme", "USD", 90_000),
            new Payment("p-3", "zeta", "RUB", 120_000), new Payment("p-4", "beta", "RUB", 70_000),
            new Payment("p-5", "zeta", "EUR", 300), new Payment("p-6", "beta", "RUB", 65_000),
            new Payment("p-7", "acme", "RUB", 40_000));

    public static void main(String[] args) {
        System.out.println("== top-2 clients by total: group, then sort entries by value");
        Map<String, Long> totals = BATCH.stream().collect(groupingBy(Payment::client, summingLong(Payment::amount)));
        List<String> top = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(2)
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
        System.out.println(top);

        System.out.println("== top-2 payments inside each currency");
        Map<String, List<String>> top2 = BATCH.stream().collect(groupingBy(Payment::currency, TreeMap::new,
                collectingAndThen(toList(), l -> l.stream()
                        .sorted(Comparator.comparingLong(Payment::amount).reversed())
                        .limit(2).map(Payment::id).toList())));
        System.out.println(top2);

        System.out.println("== most frequent currency");
        Map.Entry<String, Long> most = BATCH.stream().collect(groupingBy(Payment::currency, counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        System.out.println(most);

        System.out.println("== index by id, keeping order of appearance");
        Map<String, Payment> index = BATCH.stream().collect(toMap(Payment::id, p -> p, (a, b) -> a, LinkedHashMap::new));
        System.out.println(index.keySet());
    }
}
