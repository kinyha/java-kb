package kb.payments;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import kb.payments.Payments.Payment;
import kb.payments.Payments.PaymentRepository;

/** Fake: настоящая, упрощённая реализация — работает, но в памяти. Сверена контрактом с JDBC. */
class InMemoryPaymentRepository implements PaymentRepository {
    final Map<String, Payment> rows = new HashMap<>();
    public Optional<Payment> findById(String id) { return Optional.ofNullable(rows.get(id)); }
    public boolean existsById(String id) { return rows.containsKey(id); }
    public void save(Payment p) {
        if (rows.putIfAbsent(p.id(), p) != null) throw new IllegalStateException("payment " + p.id() + " already exists");
    }
}
