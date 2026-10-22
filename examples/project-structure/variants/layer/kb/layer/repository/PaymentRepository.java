package kb.layer.repository;

import kb.layer.model.Payment;

public interface PaymentRepository {              // Сервис в другом пакете: только public.
    void save(Payment payment);
    Payment find(String id);
}
