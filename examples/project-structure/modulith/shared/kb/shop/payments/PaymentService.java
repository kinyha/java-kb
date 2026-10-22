package kb.shop.payments;

import kb.shop.payments.persistence.PaymentRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {                     // API модуля payments: лежит в его корневом пакете.
    private final PaymentRepository repository;

    PaymentService(PaymentRepository repository) { this.repository = repository; }

    public Payment find(String id) { return repository.find(id); }
}
