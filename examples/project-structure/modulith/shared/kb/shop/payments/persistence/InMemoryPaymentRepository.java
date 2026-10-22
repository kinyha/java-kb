package kb.shop.payments.persistence;

import kb.shop.payments.Payment;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryPaymentRepository implements PaymentRepository { // Реализацию создаёт Spring, public не нужен.
    public Payment find(String id) { return new Payment(id, 2_500, "CAPTURED"); }
}
