package kb.javatesting;

public final class FeePolicy {
    public long cents(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        // 2%, ceiling to a whole cent. Division before addition avoids amount+49 overflow.
        return amount / 50 + (amount % 50 == 0 ? 0 : 1);
    }
}
