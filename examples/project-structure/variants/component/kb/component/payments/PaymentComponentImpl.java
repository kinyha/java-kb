package kb.component.payments;

class PaymentComponentImpl implements PaymentComponent { // Правила и хранение — внутри одного пакета.
    private final PaymentRepository repository;

    PaymentComponentImpl(PaymentRepository repository) { this.repository = repository; }

    public Payment pay(String id, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Payment payment = new Payment(id, amount, "APPROVED");
        repository.save(payment);
        return payment;
    }

    public Payment find(String id) { return repository.find(id); }
}
