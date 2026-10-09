import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Currency;
import java.util.Objects;
import java.util.Random;

public class MoneyDemo {
    // This model represents settled amounts in ISO minor units, not high-precision rates.
    record Money(BigDecimal amount, Currency currency) {
        Money {
            Objects.requireNonNull(currency);
            Objects.requireNonNull(amount);
            int digits = currency.getDefaultFractionDigits();
            if (digits < 0) throw new IllegalArgumentException("no supported minor unit");
            amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
        }
        Money plus(Money other) {
            if (!currency.equals(other.currency)) throw new IllegalArgumentException("currency mismatch");
            return new Money(amount.add(other.amount), currency);
        }
        long minorUnits() {
            return amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
        }
    }

    // Policy: nonnegative total, equal shares, extra units go in stable participant order.
    static long[] allocateMinorUnits(long total, int count) {
        if (total < 0 || count <= 0) throw new IllegalArgumentException("invalid allocation");
        long quotient = total / count, remainder = total % count;
        long[] shares = new long[count];
        for (int i = 0; i < count; i++) shares[i] = quotient + (i < remainder ? 1 : 0);
        return shares;
    }

    static void verifyAllocation(long total, int count) {
        long[] shares = allocateMinorUnits(total, count);
        BigInteger sum = BigInteger.ZERO;
        long min = Long.MAX_VALUE, max = 0;
        for (long share : shares) {
            if (share < 0) throw new AssertionError("negative share");
            sum = sum.add(BigInteger.valueOf(share));
            min = Math.min(min, share); max = Math.max(max, share);
        }
        if (!sum.equals(BigInteger.valueOf(total)) || max - min > 1)
            throw new AssertionError("allocation lost total or fairness");
    }

    public static void main(String[] args) {
        Currency eur = Currency.getInstance("EUR");
        Money a = new Money(new BigDecimal("7.0"), eur);
        Money b = new Money(new BigDecimal("7.00"), eur);
        System.out.println("money equal=" + a.equals(b) + ", amount=" + a.amount() + ", minor=" + a.minorUnits());
        for (String code : new String[]{"EUR", "JPY", "KWD"})
            System.out.println(code + " digits=" + Currency.getInstance(code).getDefaultFractionDigits());
        try { new Money(new BigDecimal("7.001"), eur); }
        catch (ArithmeticException expected) { System.out.println("extra fraction rejected"); }
        try { a.plus(new Money(new BigDecimal("7.00"), Currency.getInstance("USD"))); }
        catch (IllegalArgumentException expected) { System.out.println("currency mismatch rejected"); }
        try { new Money(new BigDecimal("92233720368547758.08"), eur).minorUnits(); }
        catch (ArithmeticException expected) { System.out.println("long overflow rejected"); }
        try { Math.addExact(Long.MAX_VALUE, 1); }
        catch (ArithmeticException expected) { System.out.println("minor unit addition overflow rejected"); }
        long[] shares = allocateMinorUnits(100, 3);
        System.out.println("shares=" + Arrays.toString(shares) + ", sum=" + Arrays.stream(shares).sum());
        System.out.println("truncating conversion=" + new BigDecimal("1.239").movePointRight(2).longValue());
        verifyAllocation(0, 4);
        verifyAllocation(1, 5);
        verifyAllocation(Long.MAX_VALUE, 7);
        Random random = new Random(42);
        for (int i = 0; i < 100; i++) verifyAllocation(random.nextLong() & Long.MAX_VALUE, 1 + random.nextInt(20));
        System.out.println("allocation checks=103 passed");
    }
}
