package kb.shop.app;

import kb.shop.persistence.PaymentRepository;     // Тот же обход, что и раньше, но через модуль сборки.

public class RefundController {
    private final PaymentRepository store;

    public RefundController(PaymentRepository store) { this.store = store; }

    public long amount(String id) { return store.amount(id); }
}
