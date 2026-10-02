package kb.fees;

/** Комиссия: 2 % от суммы, с 1000 ₽ — 1,5 %; партнёрам половина; не меньше 30 коп. Суммы в копейках. */
public final class FeeCalculator {
    static final long LARGE_FROM = 100_000;
    static final long MIN_FEE = 30;

    public long fee(long amountMinor, boolean partner) {
        if (amountMinor <= 0) throw new IllegalArgumentException("amount must be positive");
        long fee = amountMinor >= LARGE_FROM ? amountMinor * 15 / 1000 : amountMinor * 2 / 100;
        if (partner) fee = fee / 2;
        return Math.max(fee, MIN_FEE);
    }
}
