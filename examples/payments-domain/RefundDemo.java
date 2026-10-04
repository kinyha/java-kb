import java.util.*;

public class RefundDemo {
    enum State { PENDING, CONFIRMED, FAILED }
    record Refund(long minor, State state) {}
    static final class Payment {
        final long captured = 12_000;
        final Map<String, Refund> refunds = new LinkedHashMap<>();
        long remaining() {
            long committed = 0;
            for (Refund r : refunds.values())
                if (r.state() != State.FAILED) committed = Math.addExact(committed, r.minor());
            return Math.subtractExact(captured, committed); // И подтверждённые, и ещё ожидаемые.
        }
        boolean request(String id, long amount) {
            Refund old = refunds.get(id);
            if (old != null) {
                if (old.minor() != amount) throw new IllegalArgumentException("id conflict");
                return false;
            }
            if (amount <= 0 || amount > remaining()) throw new IllegalArgumentException("refund exceeds available");
            refunds.put(id, new Refund(amount, State.PENDING));
            return true;
        }
        void resolve(String id, State state) {
            Refund old = refunds.get(id);
            if (old == null || old.state() != State.PENDING || state == State.PENDING)
                throw new IllegalArgumentException("invalid resolution");
            refunds.put(id, new Refund(old.minor(), state));
        }
    }
    public static void main(String[] args) {
        Payment p = new Payment();
        p.request("r1", 8_000); System.out.println("pending remaining=" + p.remaining());
        try { p.request("r2", 5_000); }
        catch (IllegalArgumentException e) { System.out.println("second=" + e.getMessage()); }
        p.resolve("r1", State.CONFIRMED);
        System.out.println("confirmed remaining=" + p.remaining());
        System.out.println("retry=" + p.request("r1", 8_000));
        p.request("r3", 4_000); p.resolve("r3", State.FAILED);
        System.out.println("failed remaining=" + p.remaining());
        if (p.remaining() != 4_000) throw new AssertionError();
    }
}
