package kb.hex.web;

import kb.hex.domain.Payments;

public class RefundController {                   // Новый класс: читает платёж мимо сервиса.
    private final Payments store;                 // Тип из чужого пакета: javac проверит доступ.

    public RefundController(Payments store) { this.store = store; }

    public String status(String id) { return store.find(id).status(); }
}
