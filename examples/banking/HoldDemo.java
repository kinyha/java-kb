public class HoldDemo {
    enum State { NONE, HELD, CAPTURED, RELEASED }
    static final class Wallet {
        long posted = 100_000, held;
        State state = State.NONE;
        long available() { return Math.subtractExact(posted, held); }
        void reserve(long amount) {
            if (state != State.NONE || amount <= 0 || amount > available())
                throw new IllegalArgumentException("cannot reserve");
            held = amount; state = State.HELD;
        }
        void capture(long actual) {
            if (state != State.HELD) throw new IllegalStateException("hold is " + state);
            if (actual <= 0 || actual > held) throw new IllegalArgumentException("capture exceeds hold");
            posted = Math.subtractExact(posted, actual);
            held = 0; state = State.CAPTURED; // В примере захват окончательный, остаток резерва снят.
        }
        void release() {
            if (state != State.HELD) throw new IllegalStateException("hold is " + state);
            held = 0; state = State.RELEASED;
        }
        void show(String tag) {
            System.out.printf("%s posted=%d held=%d available=%d state=%s%n",
                tag, posted, held, available(), state);
        }
    }
    public static void main(String[] args) {
        Wallet w = new Wallet(); w.reserve(30_000); w.show("reserve");
        w.capture(28_000); w.show("capture");
        if (w.available() != 72_000) throw new AssertionError();
        Wallet cancelled = new Wallet(); cancelled.reserve(30_000);
        cancelled.release(); cancelled.show("release");
        try { cancelled.capture(28_000); }
        catch (IllegalStateException e) { System.out.println("late-capture=" + e.getMessage()); }
        cancelled.show("after-late");
    }
}
