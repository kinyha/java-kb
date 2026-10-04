import java.util.*;
import java.util.stream.*;
import static java.util.stream.Collectors.*;

// Запуск: java CollectorsDemo.java   (JDK 21, без зависимостей)
public class CollectorsDemo {
    record Payment(String id, String client, String currency, long amount, String status) {}
    static final List<Payment> BATCH = List.of(
            new Payment("p-1", "acme", "RUB", 500, "PAID"),
            new Payment("p-2", "acme", "USD", 90_000, "PAID"),
            new Payment("p-3", "zeta", "RUB", 120_000, "FAILED"),
            new Payment("p-4", "beta", "RUB", 70_000, "PAID"),
            new Payment("p-5", "zeta", "EUR", 300, null));            // статус ещё неизвестен

    public static void main(String[] args) {
        System.out.println("== toMap with a duplicate key");
        try { BATCH.stream().collect(toMap(Payment::client, Payment::amount)); }
        catch (IllegalStateException e) { System.out.println("IllegalStateException: " + e.getMessage()); }
        Map<String, Long> total = BATCH.stream().collect(toMap(Payment::client, Payment::amount, Long::sum, TreeMap::new));
        System.out.println("with merge Long::sum into TreeMap: " + total);
        try { BATCH.stream().collect(toMap(Payment::id, Payment::status)); }
        catch (NullPointerException e) { System.out.println("toMap with a null value: NullPointerException"); }

        System.out.println("== groupingBy and downstream collectors");
        Map<String, List<Payment>> byCurrency = BATCH.stream().collect(groupingBy(Payment::currency));
        System.out.println("groupingBy -> " + byCurrency.getClass().getSimpleName() + " of " + byCurrency.get("RUB").getClass().getSimpleName()
                + ", RUB ids " + byCurrency.get("RUB").stream().map(Payment::id).toList());
        System.out.println("counting:      " + new TreeMap<>(BATCH.stream().collect(groupingBy(Payment::currency, counting()))));
        System.out.println("summingLong:   " + new TreeMap<>(BATCH.stream().collect(groupingBy(Payment::currency, summingLong(Payment::amount)))));
        System.out.println("mapping ids:   " + BATCH.stream().collect(groupingBy(Payment::client, TreeMap::new, mapping(Payment::id, joining(",")))));
        System.out.println("max per client: " + BATCH.stream().collect(groupingBy(Payment::client, TreeMap::new,
                collectingAndThen(maxBy(Comparator.comparingLong(Payment::amount)), o -> o.get().id()))));
        System.out.println("two levels:    " + BATCH.stream().collect(groupingBy(Payment::client, TreeMap::new, groupingBy(Payment::currency, TreeMap::new, counting()))));
        try { BATCH.stream().collect(groupingBy(Payment::status)); }
        catch (NullPointerException e) { System.out.println("groupingBy a null key: NullPointerException (" + e.getMessage() + ")"); }

        System.out.println("== partitioningBy always has both keys");
        System.out.println("big (> 1 000 000): " + BATCH.stream().collect(partitioningBy(p -> p.amount() > 1_000_000, counting())));
        System.out.println("groupingBy same:   " + BATCH.stream().collect(groupingBy(p -> p.amount() > 1_000_000, counting())));

        System.out.println("== joining, teeing, summarizing");
        System.out.println(BATCH.stream().map(Payment::id).collect(joining(", ", "[", "]")));
        System.out.println("teeing min/max: " + BATCH.stream().collect(teeing(
                minBy(Comparator.comparingLong(Payment::amount)), maxBy(Comparator.comparingLong(Payment::amount)),
                (mn, mx) -> mn.get().id() + ".." + mx.get().id())));
        LongSummaryStatistics stats = BATCH.stream().collect(summarizingLong(Payment::amount));
        System.out.println("stats: count " + stats.getCount() + ", sum " + stats.getSum() + ", min " + stats.getMin() + ", max " + stats.getMax() + ", avg " + stats.getAverage());

        System.out.println("== a collector is four functions");
        int[] combines = {0};
        Collector<Payment, long[], Long> sum = Collector.of(
                () -> new long[1],                                    // supplier: пустой контейнер
                (acc, p) -> acc[0] += p.amount(),                     // accumulator: добавить элемент
                (a, b) -> { combines[0]++; a[0] += b[0]; return a; }, // combiner: слить два контейнера
                acc -> acc[0]);                                       // finisher: результат
        long seq = BATCH.stream().collect(sum);
        System.out.println("sequential: " + seq + ", combiner calls " + combines[0]);
        combines[0] = 0;
        long par = BATCH.parallelStream().collect(sum);
        System.out.println("parallel:   " + par + ", combiner calls " + combines[0]);
    }
}
