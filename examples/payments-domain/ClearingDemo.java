import java.util.*;

public class ClearingDemo {
    record Purchase(String issuer, String acquirer, long minor) {}
    public static void main(String[] args) {
        List<Purchase> batch = List.of(
            new Purchase("A", "B", 12_000), new Purchase("A", "B", 8_000),
            new Purchase("B", "A", 5_000)); // Оба банка играют разные роли в разных покупках.
        Map<String, Long> netDebit = new TreeMap<>();
        long gross = 0;
        for (Purchase p : batch) {
            gross = Math.addExact(gross, p.minor());
            netDebit.merge(p.issuer(), p.minor(), Math::addExact);
            netDebit.merge(p.acquirer(), -p.minor(), Math::addExact);
        }
        System.out.println("gross=" + gross);
        System.out.println("net-debit=" + netDebit);
        System.out.println("settlement A->B=" + netDebit.get("A"));
        if (netDebit.get("A") != 15_000 || netDebit.get("B") != -15_000)
            throw new AssertionError();
    }
}
