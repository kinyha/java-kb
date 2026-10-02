import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Запуск: java PagingDemo.java   (JDK 21)
public class PagingDemo {
    record Payment(long id, String status) {}

    // Таблица платежей; лента отдаётся от новых к старым (id по убыванию).
    static final List<Payment> table = new ArrayList<>();

    static List<Payment> newestFirst() {
        return table.stream().sorted(Comparator.comparingLong(Payment::id).reversed()).toList();
    }

    // LIMIT/OFFSET: страница — позиция в текущем списке.
    static List<Long> offsetPage(int offset, int size) {
        return newestFirst().stream().skip(offset).limit(size).map(Payment::id).toList();
    }

    // Keyset (курсор): страница — «всё, что старше последнего увиденного id».
    static List<Long> cursorPage(Long afterId, int size) {
        return newestFirst().stream().filter(p -> afterId == null || p.id() < afterId)
                .limit(size).map(Payment::id).toList();
    }

    enum Status { AUTHORIZED, CAPTURED, REFUNDED }

    // Строгий клиент: неизвестное значение — исключение.
    static Status strict(String s) { return Status.valueOf(s); }

    // Терпимый клиент: неизвестное значение — пустой результат, решение за вызывающим.
    static Optional<Status> tolerant(String s) {
        try { return Optional.of(Status.valueOf(s)); } catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    public static void main(String[] args) {
        for (long id = 101; id <= 106; id++) table.add(new Payment(id, "CAPTURED"));

        System.out.println("== offset paging while a payment arrives");
        System.out.println("page 1 (offset 0): " + offsetPage(0, 3));
        table.add(new Payment(107, "CAPTURED"));     // Новый платёж между запросами.
        System.out.println("page 2 (offset 3): " + offsetPage(3, 3));

        table.removeIf(p -> p.id() == 107);
        System.out.println("== cursor paging while a payment arrives");
        List<Long> first = cursorPage(null, 3);
        System.out.println("page 1: " + first + ", next cursor=" + first.get(first.size() - 1));
        table.add(new Payment(107, "CAPTURED"));
        System.out.println("page 2 (after " + first.get(2) + "): " + cursorPage(first.get(2), 3));

        System.out.println("== a new enum value from the server");
        for (String value : List.of("CAPTURED", "REFUND_PENDING")) {
            try {
                System.out.println("strict " + value + " -> " + strict(value));
            } catch (IllegalArgumentException e) {
                System.out.println("strict " + value + " -> " + e.getClass().getSimpleName());
            }
            System.out.println("tolerant " + value + " -> " + tolerant(value).map(Enum::name).orElse("UNKNOWN"));
        }
    }
}
