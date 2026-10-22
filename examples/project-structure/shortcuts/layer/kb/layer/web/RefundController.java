package kb.layer.web;

import kb.layer.repository.PaymentRepository;

public class RefundController {                   // Новый класс: читает платёж мимо сервиса.
    private final PaymentRepository store;        // Тип из чужого пакета: javac проверит доступ.

    public RefundController(PaymentRepository store) { this.store = store; }

    public String status(String id) { return store.find(id).status(); }
}
