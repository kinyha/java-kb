public class StageDemo {
    static final class Payment {
        final long requested = 12_000;
        long authorised, captureRequested, captured, settledGross, paidOut;
        String captureState = "NONE";
        void authorise() { authorised = requested; }
        void requestCapture() {
            captureRequested = authorised; captureState = "REQUESTED";
        }
        void confirmCapture() {
            captured = captureRequested; captureState = "CONFIRMED";
        }
        void failCapture() { captureState = "FAILED"; }
        void settle() {
            if (captured == 0) throw new IllegalStateException("not captured");
            settledGross = captured;
        }
        void payout(long fee) {
            if (settledGross == 0 || fee < 0 || fee > settledGross)
                throw new IllegalArgumentException("invalid payout");
            paidOut = settledGross - fee;
        }
        void show(String tag) {
            System.out.printf("%s auth=%d capture=%s captured=%d settled=%d paid=%d%n",
                tag, authorised, captureState, captured, settledGross, paidOut);
        }
    }
    static void authorisation() {
        Payment p = new Payment(); p.authorise(); p.show("approved");
        if (p.paidOut != 0) throw new AssertionError();
    }
    static void captureAndPayout() {
        Payment p = new Payment(); p.authorise();
        p.requestCapture(); p.show("accepted-command");
        p.confirmCapture(); p.show("confirmed-capture");
        p.settle(); p.show("settlement-report");
        p.payout(300); p.show("merchant-payout"); // Учебная комиссия 3 RUB, не тариф провайдера.
        if (p.paidOut != 11_700) throw new AssertionError();
    }
    static void acceptedThenFailed() {
        Payment p = new Payment(); p.authorise(); p.requestCapture();
        p.failCapture(); p.show("later-failure");
        if (p.captured != 0 || p.settledGross != 0) throw new AssertionError();
    }
    public static void main(String[] args) {
        authorisation(); captureAndPayout(); acceptedThenFailed();
    }
}
