package kb.layer.service;

import kb.layer.model.Payment;
import kb.layer.repository.PaymentRepository;

public class PaymentService {                     // Контроллер в другом пакете: только public.
    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) { this.repository = repository; }

    public Payment pay(String id, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Payment payment = new Payment(id, amount, "APPROVED");
        repository.save(payment);
        return payment;
    }
}
