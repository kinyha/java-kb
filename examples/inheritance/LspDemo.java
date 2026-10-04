import java.util.*;

// Запуск: java LspDemo.java   (JDK 21, без зависимостей)
public class LspDemo {
    // Базовый контракт: любой платёж можно частично вернуть, сумма возврата ≤ остатку.
    static class Payment {
        final String id; long refundable;
        Payment(String id, long amount) { this.id = id; this.refundable = amount; }
        void refund(long amount) {
            if (amount > refundable) throw new IllegalArgumentException("amount > refundable");
            refundable -= amount;
        }
    }
    // Подкласс ужесточает предусловие: возврат только целиком.
    static class GiftCardPayment extends Payment {
        GiftCardPayment(String id, long amount) { super(id, amount); }
        @Override void refund(long amount) {
            if (amount != refundable) throw new UnsupportedOperationException("gift card: full refund only");
            super.refund(amount);
        }
    }

    // Код, написанный по контракту Payment.
    static void refundHalf(List<Payment> batch) {
        for (Payment p : batch) {
            try { p.refund(p.refundable / 2); System.out.println("   " + p.id + " refunded half, left " + p.refundable); }
            catch (RuntimeException e) { System.out.println("   " + p.id + " " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
        }
    }

    // equals при наследовании: подкласс добавил поле в сравнение.
    static class Money {
        final long amount;
        Money(long amount) { this.amount = amount; }
        @Override public boolean equals(Object o) { return o instanceof Money m && m.amount == amount; }
        @Override public int hashCode() { return Long.hashCode(amount); }
    }
    static class CurrencyMoney extends Money {
        final String currency;
        CurrencyMoney(long amount, String currency) { super(amount); this.currency = currency; }
        @Override public boolean equals(Object o) { return o instanceof CurrencyMoney m && m.amount == amount && m.currency.equals(currency); }
        @Override public int hashCode() { return Objects.hash(amount, currency); }
    }

    public static void main(String[] args) {
        System.out.println("== a subclass that strengthens the precondition");
        refundHalf(List.of(new Payment("p-1", 1000), new GiftCardPayment("g-1", 1000)));

        System.out.println("== equals across a hierarchy");
        Money plain = new Money(100);
        CurrencyMoney rub = new CurrencyMoney(100, "RUB");
        System.out.println("plain.equals(rub) = " + plain.equals(rub) + ", rub.equals(plain) = " + rub.equals(plain));
        List<Money> list = new ArrayList<>(List.of(rub));
        System.out.println("list[rub].contains(plain) = " + list.contains(plain));
        List<Money> list2 = new ArrayList<>(List.of(plain));
        System.out.println("list[plain].contains(rub) = " + list2.contains(rub));
    }
}
