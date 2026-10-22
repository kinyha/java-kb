package kb.layer.repository;

import kb.layer.model.Payment;

class JdbcPaymentRepository implements PaymentRepository { // Реализацию можно скрыть: её создаёт Spring.
    public void save(Payment payment) { }
    public Payment find(String id) { return new Payment(id, 100, "APPROVED"); }
}
