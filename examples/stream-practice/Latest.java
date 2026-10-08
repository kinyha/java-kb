import java.util.*;
import java.util.function.Function;
import static java.util.stream.Collectors.*;

public class Latest {
    record Event(String id, long version, String status) {}

    static Map<String, Event> latest(List<Event> events) {
        return events.stream().collect(toMap(Event::id, Function.identity(),
                (a, b) -> a.version() > b.version() ? a : b, // равная версия: позже во входном списке
                TreeMap::new));                              // ключи по алфавиту, не по версии
    }

    public static void main(String[] args) {
        Event paid = new Event("p1", 2, "PAID"), failed = new Event("p2", 1, "FAILED");
        check(latest(List.of(paid, new Event("p1", 1, "NEW"), failed)), Map.of("p1", paid, "p2", failed));
        check(latest(List.of()), Map.of());
        Event replacement = new Event("p1", 2, "REFUNDED");
        check(latest(List.of(paid, replacement)), Map.of("p1", replacement));
        check(latest(List.of(replacement, paid)), Map.of("p1", paid));
        check(latest(List.of(paid, paid)), Map.of("p1", paid));
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
