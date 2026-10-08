import java.util.*;
import static java.util.stream.Collectors.*;

public class TopClients {
    record Payment(String client, String currency, long minorUnits) {}

    static List<String> topClients(List<Payment> payments, String currency, int k) {
        if (k < 0) throw new IllegalArgumentException("negative k");
        Map<String, Long> totals = payments.stream()
                .filter(p -> p.currency().equals(currency)) // сначала одна валюта
                .collect(groupingBy(Payment::client, summingLong(Payment::minorUnits)));
        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey())) // стабильное правило для ничьей
                .limit(k).map(Map.Entry::getKey).toList();
    }

    public static void main(String[] args) {
        List<Payment> data = List.of(new Payment("z", "RUB", 150), new Payment("a", "RUB", 100),
                new Payment("a", "RUB", 50), new Payment("b", "RUB", 140), new Payment("b", "USD", 9_000));
        check(topClients(data, "RUB", 2), List.of("a", "z"));
        check(topClients(data, "RUB", 0), List.of());
        check(topClients(data, "RUB", 20), List.of("a", "z", "b"));
        check(topClients(data, "EUR", 2), List.of());
        check(topClients(List.of(), "RUB", 2), List.of());
        try {
            topClients(data, "RUB", -1);
            throw new AssertionError("negative k accepted");
        } catch (IllegalArgumentException expected) {
            System.out.println("OK negative k rejected");
        }
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
