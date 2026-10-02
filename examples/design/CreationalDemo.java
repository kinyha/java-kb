import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Запуск: java CreationalDemo.java   (JDK 21)
public class CreationalDemo {

    // ---------- Singleton: класс с приватным конструктором ----------
    static final class BankRegistry implements Serializable {
        static final BankRegistry INSTANCE = new BankRegistry();
        private BankRegistry() {}
    }

    // ---------- Singleton: enum ----------
    enum Banks { INSTANCE }

    // ---------- Builder с проверкой инварианта ----------
    record Payment(String id, long cents, String currency, String description, boolean recurring) {
        static Builder builder() { return new Builder(); }

        static final class Builder {
            private String id, currency = "RUB", description = "";
            private long cents;
            private boolean recurring;

            Builder id(String v) { id = v; return this; }
            Builder cents(long v) { cents = v; return this; }
            Builder currency(String v) { currency = v; return this; }
            Builder description(String v) { description = v; return this; }
            Builder recurring(boolean v) { recurring = v; return this; }

            Payment build() { // Проверка всего объекта сразу, а не каждого поля.
                Objects.requireNonNull(id, "id");
                if (cents <= 0) throw new IllegalStateException("amount must be positive, got " + cents);
                return new Payment(id, cents, currency, description, recurring);
            }
        }
    }

    // ---------- Prototype: clone() копирует ссылки ----------
    static class Tariff implements Cloneable {
        String bank;
        List<String> fees = new ArrayList<>(List.of("1.5%"));
        Tariff(String bank) { this.bank = bank; }

        @Override
        public Tariff clone() {
            try { return (Tariff) super.clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); }
        }

        Tariff(Tariff other) { // Конструктор копирования: копия списка явная.
            this.bank = other.bank;
            this.fees = new ArrayList<>(other.fees);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T roundTrip(T obj) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(obj); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (T) in.readObject(); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== singleton");
        var ctor = BankRegistry.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        System.out.println("reflection copy == INSTANCE: " + (ctor.newInstance() == BankRegistry.INSTANCE));
        System.out.println("deserialized == INSTANCE: " + (roundTrip(BankRegistry.INSTANCE) == BankRegistry.INSTANCE));
        try {
            var enumCtor = Banks.class.getDeclaredConstructor(String.class, int.class);
            enumCtor.setAccessible(true);
            enumCtor.newInstance("COPY", 1);
        } catch (IllegalArgumentException e) {
            System.out.println("enum via reflection: " + e.getMessage());
        }
        System.out.println("enum deserialized == INSTANCE: " + (roundTrip(Banks.INSTANCE) == Banks.INSTANCE));

        System.out.println("== static factory methods");
        System.out.println("List.of(1): " + List.of(1).getClass().getSimpleName()
                + ", List.of(1,2,3): " + List.of(1, 2, 3).getClass().getSimpleName());
        Integer a = 127, b = 127, c = 128, d = 128; // Автоупаковка вызывает Integer.valueOf.
        System.out.println("valueOf(127) same object: " + (a == b) + ", valueOf(128) same object: " + (c == d));

        System.out.println("== builder");
        var p = Payment.builder().id("p-1").cents(500).description("coffee").build();
        System.out.println(p);
        try {
            Payment.builder().id("p-2").currency("USD").build(); // Сумму забыли.
        } catch (IllegalStateException e) {
            System.out.println("build failed: " + e.getMessage());
        }

        System.out.println("== prototype");
        var base = new Tariff("sber");
        var cloned = base.clone();
        cloned.fees.add("promo -1%");
        System.out.println("base after changing clone: " + base.fees);
        var copied = new Tariff(base);
        copied.fees.add("vip -0.5%");
        System.out.println("base after changing copy:  " + base.fees);
    }
}
