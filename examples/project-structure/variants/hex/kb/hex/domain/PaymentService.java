package kb.hex.domain;

public class PaymentService {                     // Его вызывает адаптер из web.
    private final Payments payments;

    public PaymentService(Payments payments) { this.payments = payments; }

    public Payment pay(String id, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Payment payment = new Payment(id, amount, "APPROVED");
        payments.save(payment);
        return payment;
    }
}
