package kb.payments;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import kb.payments.Payments.Payment;
import kb.payments.Payments.PaymentRepository;
import org.junit.jupiter.api.Tag;

/** Первый вариант фейка: HashMap.put молча перезаписывает. Контракт это ловит; падает намеренно. */
@Tag("fragile")
class NaiveFakeContractTest extends PaymentRepositoryContract {
    PaymentRepository create() {
        return new PaymentRepository() {
            final Map<String, Payment> rows = new HashMap<>();
            public Optional<Payment> findById(String id) { return Optional.ofNullable(rows.get(id)); }
            public boolean existsById(String id) { return rows.containsKey(id); }
            public void save(Payment p) { rows.put(p.id(), p); }
        };
    }
}
