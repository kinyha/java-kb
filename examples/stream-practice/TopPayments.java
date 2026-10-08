import java.util.*;
import static java.util.stream.Collectors.*;

public class TopPayments {
    record Payment(String id, String currency, long minorUnits) {}

    static Map<String, List<String>> topPayments(List<Payment> payments, int k) {
        if (k < 0) throw new IllegalArgumentException("negative k");
        Comparator<Payment> rank = Comparator.comparingLong(Payment::minorUnits).reversed()
                .thenComparing(Payment::id); // reversed только для суммы: id остаётся по возрастанию
        return payments.stream().collect(groupingBy(Payment::currency, TreeMap::new,
                collectingAndThen(toList(), group -> group.stream()
                        .sorted(rank).limit(k).map(Payment::id).toList())));
    }

    public static void main(String[] args) {
        List<Payment> data = List.of(new Payment("b", "RUB", 100), new Payment("a", "RUB", 100),
                new Payment("c", "RUB", 50), new Payment("x", "USD", 900));
        check(topPayments(data, 2), Map.of("RUB", List.of("a", "b"), "USD", List.of("x")));
        check(topPayments(data, 0), Map.of("RUB", List.of(), "USD", List.of()));
        check(topPayments(data, 10), Map.of("RUB", List.of("a", "b", "c"), "USD", List.of("x")));
        check(topPayments(List.of(), 2), Map.of());
        try {
            topPayments(data, -1);
            throw new AssertionError("negative k accepted");
        } catch (IllegalArgumentException expected) {
            System.out.println("OK negative k rejected");
        }
        // Независимый эталон: сортируем весь вход циклом и выбираем первые k по каждой валюте.
        Random random = new Random(42);
        for (int test = 0; test < 100; test++) {
            List<Payment> input = new ArrayList<>();
            int size = random.nextInt(50);
            for (int i = 0; i < size; i++) {
                input.add(new Payment("p" + i, "C" + random.nextInt(3), random.nextInt(5)));
            }
            int k = random.nextInt(6);
            Map<String, List<String>> want = new TreeMap<>();
            for (Payment p : input) want.computeIfAbsent(p.currency(), ignored -> new ArrayList<>());
            input.sort((a, b) -> {
                int c = Long.compare(b.minorUnits(), a.minorUnits());
                return c != 0 ? c : a.id().compareTo(b.id());
            });
            for (Payment p : input) {
                List<String> ids = want.get(p.currency());
                if (ids.size() < k) ids.add(p.id());
            }
            if (!Objects.equals(topPayments(input, k), want)) throw new AssertionError("random case " + test);
        }
        System.out.println("OK 100 randomized cases");
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
