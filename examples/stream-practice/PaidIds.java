import java.util.*;

public class PaidIds {
    record Payment(String id, String status) {}

    static List<String> paidIds(List<Payment> payments) {
        return payments.stream()
                .filter(p -> p.status().equals("PAID")) // сначала отбираем события
                .map(Payment::id)                       // равенство теперь по id
                .distinct()                            // первый id в encounter order
                .toList();                             // неизменяемый список
    }

    public static void main(String[] args) {
        check(paidIds(List.of(new Payment("p2", "NEW"), new Payment("p1", "PAID"),
                new Payment("p2", "PAID"), new Payment("p1", "PAID"))), List.of("p1", "p2"));
        check(paidIds(List.of()), List.of());
        check(paidIds(List.of(new Payment("x", "FAILED"))), List.of());
        check(paidIds(List.of(new Payment("z", "PAID"), new Payment("a", "PAID"))), List.of("z", "a"));
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
