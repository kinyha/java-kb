package kb.hex.database;

import kb.hex.domain.Payment;
import kb.hex.domain.Payments;

class JdbcPayments implements Payments {          // Адаптер: снаружи виден только через порт.
    public void save(Payment payment) { }
    public Payment find(String id) { return new Payment(id, 100, "APPROVED"); }
}
