package kb.payments;

import kb.payments.Payments.PaymentRepository;

class InMemoryPaymentRepositoryTest extends PaymentRepositoryContract {
    PaymentRepository create() { return new InMemoryPaymentRepository(); }
}
