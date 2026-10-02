package kb.payments;

import java.util.Optional;

public final class Payments {
    private Payments() {}

    public record Payment(String id, long amount, String status) {}

    public interface PaymentRepository {
        Optional<Payment> findById(String id);
        boolean existsById(String id);
        /** Новый платёж; повторный id — IllegalStateException, запись не меняется. */
        void save(Payment p);
    }

    public interface FraudCheck { int score(long amount); }

    public interface Notifier { void paymentAccepted(String id); }

    /** Версия 1: проверяет дубль через findById. */
    public static class PaymentServiceV1 {
        final PaymentRepository repo; final FraudCheck fraud; final Notifier notifier;
        public PaymentServiceV1(PaymentRepository r, FraudCheck f, Notifier n) { repo = r; fraud = f; notifier = n; }

        public String accept(String id, long amount) {
            if (repo.findById(id).isPresent()) return "DUPLICATE";
            if (fraud.score(amount) > 80) { repo.save(new Payment(id, amount, "DECLINED")); return "DECLINED"; }
            repo.save(new Payment(id, amount, "ACCEPTED"));
            notifier.paymentAccepted(id);
            return "ACCEPTED";
        }
    }

    /** Версия 2 после рефакторинга: то же поведение, дубль проверяется через existsById. */
    public static class PaymentServiceV2 extends PaymentServiceV1 {
        public PaymentServiceV2(PaymentRepository r, FraudCheck f, Notifier n) { super(r, f, n); }

        @Override public String accept(String id, long amount) {
            if (repo.existsById(id)) return "DUPLICATE";
            String status = fraud.score(amount) > 80 ? "DECLINED" : "ACCEPTED";
            repo.save(new Payment(id, amount, status));
            if (status.equals("ACCEPTED")) notifier.paymentAccepted(id);
            return status;
        }
    }
}
