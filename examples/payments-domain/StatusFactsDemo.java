import java.util.*;

public class StatusFactsDemo {
    enum Type { AUTHORISED, CAPTURED, SETTLED, REFUNDED, DISPUTE_OPENED }
    record Event(String id, String payment, Type type, long minor) {}
    static final class Facts {
        final String payment = "psp-1";
        final Map<String, Event> seen = new HashMap<>();
        Long authorised, captured, settled;
        long refunded;
        boolean dispute;
        String observation = "WAITING";
        void timeout() { observation = "UNKNOWN"; } // Локальное знание, а не отказ банка.
        boolean apply(Event e) {
            if (!payment.equals(e.payment())) throw new IllegalArgumentException("wrong payment");
            Event old = seen.get(e.id());
            if (old != null) {
                if (!old.equals(e)) throw new IllegalArgumentException("event conflict");
                return false;
            }
            if (e.minor() <= 0) throw new IllegalArgumentException("invalid amount");
            switch (e.type()) {
                case AUTHORISED -> authorised = e.minor();
                case CAPTURED -> captured = e.minor();
                case SETTLED -> settled = e.minor();
                case REFUNDED -> refunded = Math.addExact(refunded, e.minor());
                case DISPUTE_OPENED -> dispute = true;
            }
            seen.put(e.id(), e); observation = "OBSERVED";
            return true;
        }
        static String value(Long n) { return n == null ? "unknown" : n.toString(); }
        void show(String tag) {
            String financial = settled != null ? "SETTLED" : captured != null ? "CAPTURED"
                : authorised != null ? "AUTHORISED" : "UNKNOWN";
            System.out.printf("%s observation=%s financial=%s auth=%s captured=%s settled=%s refunded=%d dispute=%s%n",
                tag, observation, financial, value(authorised), value(captured), value(settled),
                refunded, dispute ? "OPEN" : "NONE");
        }
    }
    static void uncertainAndOutOfOrder() {
        Facts f = new Facts(); f.timeout(); f.show("timeout");
        // Присланы подтверждённые сведения, но порядок доставки отличается от порядка исполнения.
        f.apply(new Event("e3", "psp-1", Type.SETTLED, 12_000)); f.show("settled-first");
        f.apply(new Event("e1", "psp-1", Type.AUTHORISED, 12_000)); f.show("late-authorisation");
        f.apply(new Event("e2", "psp-1", Type.CAPTURED, 12_000)); f.show("late-capture");
        if (f.settled != 12_000) throw new AssertionError();
    }
    static void refundsAndDispute() {
        Facts f = new Facts();
        f.apply(new Event("e1", "psp-1", Type.AUTHORISED, 12_000));
        f.apply(new Event("e2", "psp-1", Type.CAPTURED, 12_000));
        f.apply(new Event("e3", "psp-1", Type.SETTLED, 12_000));
        Event refund = new Event("r1", "psp-1", Type.REFUNDED, 4_000);
        System.out.println("refund-first=" + f.apply(refund));
        System.out.println("refund-repeat=" + f.apply(refund));
        f.apply(new Event("d1", "psp-1", Type.DISPUTE_OPENED, 12_000));
        f.show("after-refund-and-dispute");
        try { f.apply(new Event("r1", "psp-1", Type.REFUNDED, 4_100)); }
        catch (IllegalArgumentException e) { System.out.println("conflict=" + e.getMessage()); }
        if (f.refunded != 4_000) throw new AssertionError();
    }
    public static void main(String[] args) { uncertainAndOutOfOrder(); refundsAndDispute(); }
}
