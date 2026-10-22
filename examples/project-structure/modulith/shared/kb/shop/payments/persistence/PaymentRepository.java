package kb.shop.payments.persistence;

import kb.shop.payments.Payment;

public interface PaymentRepository {              // public вынужденно: им пользуется родительский пакет.
    Payment find(String id);
}
