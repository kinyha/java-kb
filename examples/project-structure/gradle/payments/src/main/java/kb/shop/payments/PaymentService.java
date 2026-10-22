package kb.shop.payments;

import kb.shop.persistence.PaymentRepository;

public class PaymentService {
    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) { this.repository = repository; }

    public long amount(String id) { return repository.amount(id); }
}
