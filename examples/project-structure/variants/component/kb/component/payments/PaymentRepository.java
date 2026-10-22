package kb.component.payments;

interface PaymentRepository {
    void save(Payment payment);
    Payment find(String id);
}
