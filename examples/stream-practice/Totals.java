import java.util.*;
import static java.util.stream.Collectors.*;

public class Totals {
    record Payment(String client, String currency, long minorUnits, boolean paid) {}

    static Map<String, Map<String, Long>> totals(List<Payment> payments) {
        return payments.stream().filter(Payment::paid)
                .collect(groupingBy(Payment::client, TreeMap::new,
                        groupingBy(Payment::currency, TreeMap::new,
                                summingLong(Payment::minorUnits)))); // сумма отдельно в каждой валюте
    }

    public static void main(String[] args) {
        check(totals(List.of(new Payment("a", "RUB", 100, true), new Payment("a", "USD", 100, true),
                new Payment("a", "RUB", 250, true), new Payment("b", "RUB", 900, false))),
                Map.of("a", Map.of("RUB", 350L, "USD", 100L)));
        check(totals(List.of()), Map.of());
        check(totals(List.of(new Payment("a", "RUB", 100, false))), Map.of());
        check(totals(List.of(new Payment("b", "EUR", 0, true))), Map.of("b", Map.of("EUR", 0L)));
        check(totals(List.of(new Payment("x", "RUB", 2_000_000_000L, true),
                new Payment("x", "RUB", 2_000_000_000L, true))), Map.of("x", Map.of("RUB", 4_000_000_000L)));
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
