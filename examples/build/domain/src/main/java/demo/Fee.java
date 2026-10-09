package demo;

public final class Fee {
    private Fee() {}

    public static long minorUnits(long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative amount");
        return Math.addExact(amount, 49) / 50; // 2%, rounded up in integer minor units.
    }
}
