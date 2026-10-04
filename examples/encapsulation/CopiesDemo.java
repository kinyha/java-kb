import java.lang.reflect.Field;
import java.util.*;

// Запуск: java CopiesDemo.java   (JDK 21, без зависимостей)
public class CopiesDemo {
    // Принимает список от вызывающего и хранит его как есть.
    static final class LeakyBatch {
        private final List<String> ids;
        LeakyBatch(List<String> ids) { this.ids = ids; }
        List<String> ids() { return Collections.unmodifiableList(ids); }
    }
    // Защитная копия на входе; наружу — неизменяемый снимок.
    static final class Batch {
        private final List<String> ids;
        Batch(List<String> ids) { this.ids = List.copyOf(ids); }
        List<String> ids() { return ids; }
    }

    // Изменяемый ключ в HashSet.
    static final class MutableMoney {
        long amount; final String currency;
        MutableMoney(long amount, String currency) { this.amount = amount; this.currency = currency; }
        @Override public boolean equals(Object o) { return o instanceof MutableMoney m && m.amount == amount && m.currency.equals(currency); }
        @Override public int hashCode() { return Objects.hash(amount, currency); }
    }
    record Money(long amount, String currency) {
        Money plus(long delta) { return new Money(amount + delta, currency); }   // новый объект вместо изменения
    }

    static final class Holder { private final long limit = Long.parseLong("100"); }   // не константа

    public static void main(String[] args) throws Exception {
        System.out.println("== unmodifiable view vs copy");
        List<String> source = new ArrayList<>(List.of("p-1", "p-2"));
        LeakyBatch leaky = new LeakyBatch(source);
        Batch safe = new Batch(source);
        source.add("p-evil");
        System.out.println("unmodifiableList view: " + leaky.ids());
        System.out.println("List.copyOf snapshot:  " + safe.ids());
        List<String> imm = List.of("a");
        System.out.println("List.copyOf(immutable) returns same instance: " + (List.copyOf(imm) == imm));

        System.out.println("== mutable key in a HashSet");
        Set<MutableMoney> seen = new HashSet<>();
        MutableMoney m = new MutableMoney(100, "RUB");
        seen.add(m);
        m.amount = 200;                                          // хеш изменился, корзина — нет
        System.out.println("contains after mutation: " + seen.contains(m) + ", size = " + seen.size());
        Money price = new Money(100, "RUB");
        Money withFee = price.plus(15);
        System.out.println("immutable: price = " + price.amount() + ", withFee = " + withFee.amount());

        System.out.println("== reflection vs final");
        Holder h = new Holder();
        Field limit = Holder.class.getDeclaredField("limit");
        limit.setAccessible(true);
        limit.setLong(h, 1_000_000);
        System.out.println("final field of a class changed via reflection: " + limit.getLong(h));
        Field amount = Money.class.getDeclaredField("amount");
        amount.setAccessible(true);
        try { amount.setLong(price, 0); }
        catch (IllegalAccessException e) { System.out.println("record field: IllegalAccessException"); }
    }
}
