import java.io.*;
import java.util.*;

// Запуск: java OptionalDemo.java   (JDK 21, без зависимостей)
public class OptionalDemo {
    record Bank(String bic) {}
    record Card(String mask, Bank bank) {}
    record Customer(String id, Card card) {}

    static final Map<String, Customer> DB = Map.of(
            "c-1", new Customer("c-1", new Card("4111…1111", new Bank("SABRRUMM"))),
            "c-2", new Customer("c-2", new Card("5500…0004", null)),     // банк неизвестен
            "c-3", new Customer("c-3", null));                            // карты нет

    static Optional<Customer> find(String id) { return Optional.ofNullable(DB.get(id)); }
    static Optional<Card> card(Customer c) { return Optional.ofNullable(c.card()); }

    static int defaultCalls;
    static String loadDefaultBic() { defaultCalls++; return "DEFAULTBIC"; }     // «дорогой» вызов

    public static void main(String[] args) throws Exception {
        System.out.println("== null checks vs Optional chain");
        for (String id : List.of("c-1", "c-2", "c-3", "c-9")) {
            String viaNull;
            try { viaNull = DB.get(id).card().bank().bic(); }
            catch (NullPointerException e) { viaNull = "NPE"; }
            String viaOptional = find(id)
                    .flatMap(OptionalDemo::card)          // метод уже возвращает Optional
                    .map(Card::bank)                      // может вернуть null — станет empty
                    .map(Bank::bic)
                    .orElse("unknown");
            System.out.printf("%s: chained getters -> %-8s Optional -> %s%n", id, viaNull, viaOptional);
        }

        System.out.println("== orElse is eager, orElseGet is lazy");
        defaultCalls = 0;
        find("c-1").map(c -> c.card().bank().bic()).orElse(loadDefaultBic());
        System.out.println("value present, orElse(load()):       load called " + defaultCalls + " time(s)");
        defaultCalls = 0;
        find("c-1").map(c -> c.card().bank().bic()).orElseGet(OptionalDemo::loadDefaultBic);
        System.out.println("value present, orElseGet(load):      load called " + defaultCalls + " time(s)");

        System.out.println("== creating and unwrapping");
        try { Optional.of(null); } catch (NullPointerException e) { System.out.println("Optional.of(null): NullPointerException"); }
        try { find("c-9").get(); } catch (NoSuchElementException e) { System.out.println("empty.get(): " + e.getMessage()); }
        try { find("c-9").orElseThrow(() -> new IllegalArgumentException("customer c-9 not found")); }
        catch (IllegalArgumentException e) { System.out.println("orElseThrow(supplier): " + e.getMessage()); }
        System.out.println("map(x -> null) = " + Optional.of("x").map(x -> null) + ", filter = " + Optional.of(500L).filter(a -> a > 1000));
        System.out.println("Optional.empty() is a singleton: " + ((Object) Optional.empty() == find("c-9")));

        System.out.println("== Optional is not for fields");
        record Profile(String id, Optional<String> phone) implements Serializable {}
        try (var out = new ObjectOutputStream(new ByteArrayOutputStream())) { out.writeObject(new Profile("c-1", Optional.of("+7…"))); }
        catch (NotSerializableException e) { System.out.println("serialize record with Optional field: NotSerializableException " + e.getMessage()); }

        System.out.println("== streams of Optionals");
        List<String> bics = List.of("c-1", "c-2", "c-3", "c-9").stream()
                .map(OptionalDemo::find)
                .flatMap(Optional::stream)                       // Java 9: пустые пропадают
                .flatMap(c -> card(c).stream())
                .map(Card::bank).filter(Objects::nonNull).map(Bank::bic).toList();
        System.out.println("known BICs: " + bics);
        OptionalLong max = List.of(500L, 70L, 1200L).stream().mapToLong(Long::longValue).max();
        System.out.println("OptionalLong max = " + max.getAsLong() + ", of empty = " + emptyMax());
    }
    static OptionalLong emptyMax() { return java.util.stream.LongStream.empty().max(); }
}
