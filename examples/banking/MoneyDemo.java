import java.math.*;

public class MoneyDemo {
    record Money(String currency, long minor) {
        Money add(Money other) {
            if (!currency.equals(other.currency)) throw new IllegalArgumentException("currency mismatch");
            return new Money(currency, Math.addExact(minor, other.minor));
        }
    }
    static long rubMinor(String amount) {
        return new BigDecimal(amount).setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2).longValueExact(); // Контракт примера: RUB, две десятичные цифры.
    }
    static void binary() {
        System.out.println("double=" + (0.1 + 0.2));
        System.out.println("constructor=" + new BigDecimal(0.1).toPlainString());
        System.out.println("text=" + new BigDecimal("0.1").add(new BigDecimal("0.2")));
    }
    static void rounding() {
        BigDecimal gross = new BigDecimal("0.50"), rate = new BigDecimal("0.01");
        BigDecimal each = gross.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal separately = each.multiply(BigDecimal.valueOf(3));
        BigDecimal together = gross.multiply(BigDecimal.valueOf(3)).multiply(rate)
            .setScale(2, RoundingMode.HALF_UP);
        System.out.println("per-payment=" + separately + " per-batch=" + together);
        if (!separately.equals(new BigDecimal("0.03"))) throw new AssertionError();
    }
    static void validation() {
        System.out.println("minor=" + rubMinor("123.00"));
        try { rubMinor("1.001"); }
        catch (ArithmeticException e) { System.out.println("extra-fraction=rejected"); }
        try { new Money("RUB", 100).add(new Money("USD", 100)); }
        catch (IllegalArgumentException e) { System.out.println("mixed=" + e.getMessage()); }
        try { new Money("RUB", Long.MAX_VALUE).add(new Money("RUB", 1)); }
        catch (ArithmeticException e) { System.out.println("overflow=rejected"); }
    }
    public static void main(String[] args) { binary(); rounding(); validation(); }
}
