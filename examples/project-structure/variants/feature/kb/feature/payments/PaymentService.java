package kb.feature.payments;

class PaymentService {                            // Все соседи в том же пакете: public не нужен.
    private final PaymentRepository repository;

    PaymentService(PaymentRepository repository) { this.repository = repository; }

    public Payment pay(String id, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Payment payment = new Payment(id, amount, "APPROVED");
        repository.save(payment);
        return payment;
    }
}
