package kb.hex.web;

import kb.hex.database.JdbcPayments;

public class RefundController {                   // Новый класс: читает платёж мимо сервиса.
    private final JdbcPayments store;             // Тип из чужого пакета: javac проверит доступ.

    public RefundController(JdbcPayments store) { this.store = store; }

    public String status(String id) { return store.find(id).status(); }
}
