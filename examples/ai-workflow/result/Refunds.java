public class Refunds {
    public static long refund(long paid, long fee, long alreadyRefunded, long requested) {
        if (requested <= 0) {
            throw new IllegalArgumentException("Requested amount must be positive");
        }

        long balance = paid - fee - alreadyRefunded;

        if (requested > balance) {
            throw new IllegalArgumentException("Requested amount exceeds available balance");
        }

        return requested;
    }
}
