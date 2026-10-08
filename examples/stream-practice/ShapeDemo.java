import java.util.*;
import static java.util.stream.Collectors.*;

public class ShapeDemo {
    record Payment(String client, String currency, long minorUnits, boolean paid) {}
    record Order(String client, List<String> products) {}

    public static void main(String[] args) {
        List<Payment> batch = List.of(new Payment("a", "RUB", 100, true),
                new Payment("a", "USD", 100, true), new Payment("a", "RUB", 250, true),
                new Payment("b", "RUB", 900, false));
        Map<String, Long> totals = batch.stream().filter(Payment::paid)
                .collect(groupingBy(Payment::currency, TreeMap::new, summingLong(Payment::minorUnits)));
        System.out.println(totals);
        Map<String, Long> before = batch.stream().filter(Payment::paid)
                .collect(groupingBy(Payment::client, TreeMap::new, counting()));
        Map<String, Long> inside = batch.stream().collect(groupingBy(Payment::client, TreeMap::new,
                filtering(Payment::paid, counting()))); // b остаётся с нулевым счётчиком
        System.out.println(before);
        System.out.println(inside);
        List<Order> orders = List.of(new Order("a", List.of("tea", "milk")), new Order("b", List.of()));
        System.out.println(orders.stream().flatMap(o -> o.products().stream()).toList());
        Map<String, Set<String>> byClient = orders.stream().collect(groupingBy(Order::client, TreeMap::new,
                flatMapping(o -> o.products().stream(), toCollection(TreeSet::new))));
        System.out.println(byClient);
    }
}
