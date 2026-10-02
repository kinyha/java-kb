import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Четыре самостоятельных опыта; java DddDemo.java values|aggregate|race|events. */
public class DddDemo {
    record Money(long minor, String currency) {
        Money {
            if (minor < 0) throw new IllegalArgumentException("negative money");
            currency = Objects.requireNonNull(currency).toUpperCase(Locale.ROOT);
            java.util.Currency.getInstance(currency); // Проверяем код валюты.
        }
        Money plus(Money other) {
            sameCurrency(other);
            return new Money(Math.addExact(minor, other.minor), currency);
        }
        Money minus(Money other) {
            sameCurrency(other);
            return new Money(Math.subtractExact(minor, other.minor), currency);
        }
        void sameCurrency(Money other) {
            if (!currency.equals(other.currency)) throw new IllegalArgumentException("currency mismatch");
        }
    }
    record RawMoney(BigDecimal amount, String currency) {}
    record RawQuote(List<Money> fees) {}
    record Quote(List<Money> fees) {
        Quote { fees = List.copyOf(fees); } // Элементы Money тоже неизменяемы.
    }
    record PaymentId(String value) {
        PaymentId { if (value == null || value.isBlank()) throw new IllegalArgumentException("empty id"); }
    }
    static final class Refund {
        final String id; // Идентичность возврата сохраняется при изменении его состояния.
        final Money amount;
        Refund(String id, Money amount) { this.id = id; this.amount = amount; }
    }
    record RefundRequested(PaymentId paymentId, String refundId, Money amount) {}

    static final class Payment {
        final PaymentId id;
        final Money captured;
        private Money reserved;
        private final LinkedHashMap<String, Refund> refunds = new LinkedHashMap<>();
        private final List<RefundRequested> pending = new ArrayList<>();

        Payment(PaymentId id, Money captured) {
            this.id = Objects.requireNonNull(id); this.captured = Objects.requireNonNull(captured);
            if (captured.minor == 0) throw new IllegalArgumentException("empty capture");
            reserved = new Money(0, captured.currency);
        }
        boolean requestRefund(String refundId, Money amount) {
            if (refundId == null || refundId.isBlank()) throw new IllegalArgumentException("empty refund id");
            Objects.requireNonNull(amount);
            captured.sameCurrency(amount);
            if (amount.minor == 0) throw new IllegalArgumentException("empty refund");
            var previous = refunds.get(refundId);
            if (previous != null) {
                if (!previous.amount.equals(amount)) throw new IllegalStateException("refund id reused");
                return false; // Повтор той же команды не резервирует сумму ещё раз.
            }
            var next = reserved.plus(amount);
            if (next.minor > captured.minor) throw new IllegalStateException("refund exceeds capture");
            // Все проверки прошли: теперь меняем состояние только через корень.
            refunds.put(refundId, new Refund(refundId, amount));
            reserved = next;
            pending.add(new RefundRequested(id, refundId, amount));
            return true;
        }
        Money available() { return captured.minus(reserved); }
        Money reserved() { return reserved; }
        List<Refund> refunds() { return List.copyOf(refunds.values()); }
        List<RefundRequested> events() { return List.copyOf(pending); }
        Payment storedCopy() {
            var copy = new Payment(id, captured);
            copy.reserved = reserved; // Money неизменяем: разделять ссылку безопасно.
            refunds.forEach((key, value) -> copy.refunds.put(key, new Refund(value.id, value.amount)));
            return copy; // Новая загрузка не создаёт повторно прежние события.
        }
    }

    record Loaded(Payment payment, long version) {}
    static final class Store {
        private Payment stored = new Payment(new PaymentId("p-17"), new Money(1_000, "RUB"));
        private long version;
        private final List<RefundRequested> outbox = new ArrayList<>();
        boolean failNextCommit;
        synchronized Loaded load() { return new Loaded(stored.storedCopy(), version); }
        synchronized long commit(Loaded loaded) {
            if (loaded.version != version) throw new IllegalStateException("version conflict");
            if (failNextCommit) {
                failNextCommit = false;
                throw new IllegalStateException("commit failed"); // До изменения обоих хранилищ.
            }
            stored = loaded.payment.storedCopy();
            outbox.addAll(loaded.payment.events()); // В этом опыте состояние и outbox меняются вместе.
            return ++version;
        }
        synchronized int outboxSize() { return outbox.size(); }
    }

    static void values() {
        var first = new RawMoney(new BigDecimal("10.0"), "RUB");
        var second = new RawMoney(new BigDecimal("10.00"), "RUB");
        System.out.println("raw decimal equality=" + first.equals(second));
        System.out.println("minor equality=" + new Money(1_000, "rub").equals(new Money(1_000, "RUB")));
        try { new Money(1_000, "RUB").plus(new Money(100, "USD")); }
        catch (IllegalArgumentException error) { System.out.println("mixed currencies=" + error.getMessage()); }
        var source = new ArrayList<>(List.of(new Money(10, "RUB")));
        var raw = new RawQuote(source); var copied = new Quote(source);
        source.add(new Money(20, "RUB")); // Record не копирует список автоматически.
        System.out.println("raw record fees=" + raw.fees.size() + ", copied fees=" + copied.fees.size());
    }
    static void aggregate() {
        var payment = new Payment(new PaymentId("p-17"), new Money(1_000, "RUB"));
        System.out.println("first accepted=" + payment.requestRefund("r-1", new Money(700, "RUB")));
        System.out.println("duplicate accepted=" + payment.requestRefund("r-1", new Money(700, "RUB")));
        try { payment.requestRefund("r-2", new Money(400, "RUB")); }
        catch (IllegalStateException error) { System.out.println("second request=" + error.getMessage()); }
        try { payment.refunds().clear(); }
        catch (UnsupportedOperationException error) { System.out.println("collection change=" + error.getClass().getSimpleName()); }
        System.out.println("reserved=" + payment.reserved.minor + ", available=" + payment.available().minor
                + ", refunds=" + payment.refunds.size() + ", pending events=" + payment.pending.size());
    }
    static void race() {
        var store = new Store();
        var first = store.load(); var second = store.load(); // Два независимых снимка version=0.
        first.payment.requestRefund("r-1", new Money(700, "RUB"));
        second.payment.requestRefund("r-2", new Money(700, "RUB"));
        System.out.println("local available=" + first.payment.available().minor + "," + second.payment.available().minor);
        System.out.println("first version=" + store.commit(first));
        try { store.commit(second); }
        catch (IllegalStateException error) { System.out.println("second commit=" + error.getMessage()); }
        var retry = store.load(); // Повтор читает уже принятую сумму 700.
        try { retry.payment.requestRefund("r-2", new Money(700, "RUB")); }
        catch (IllegalStateException error) { System.out.println("retry=" + error.getMessage()); }
        System.out.println("stored reserved=" + store.load().payment.reserved.minor);
    }
    static void events() {
        var store = new Store();
        var loaded = store.load();
        loaded.payment.requestRefund("r-1", new Money(400, "RUB"));
        System.out.println("before commit: stored=" + store.load().payment.reserved.minor
                + ", outbox=" + store.outboxSize() + ", pending=" + loaded.payment.events().size());
        store.failNextCommit = true;
        try { store.commit(loaded); }
        catch (IllegalStateException error) { System.out.println(error.getMessage()); }
        System.out.println("after failure: stored=" + store.load().payment.reserved.minor
                + ", outbox=" + store.outboxSize() + ", old object=" + loaded.payment.reserved.minor);
        var retry = store.load(); // Старый изменённый объект в следующую попытку не переносим.
        retry.payment.requestRefund("r-1", new Money(400, "RUB"));
        store.commit(retry);
        System.out.println("after retry: stored=" + store.load().payment.reserved.minor
                + ", outbox=" + store.outboxSize() + ", new load pending=" + store.load().payment.events().size());
    }
    public static void main(String[] args) {
        switch (args.length == 0 ? "aggregate" : args[0]) {
            case "values" -> values();
            case "aggregate" -> aggregate();
            case "race" -> race();
            case "events" -> events();
            default -> throw new IllegalArgumentException("values | aggregate | race | events");
        }
    }
}
