package kb.feature.payments;

class JdbcPaymentRepository implements PaymentRepository {
    public void save(Payment payment) { }
    public Payment find(String id) { return new Payment(id, 100, "APPROVED"); }
}
