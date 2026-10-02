package kb.testing;

import kb.testing.Payments.Payment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private final JdbcClient jdbc;

    PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
        PaymentApp.CONTEXTS.incrementAndGet(); // Один репозиторий на полный контекст.
    }

    public Payment save(String account, long cents, String status) {
        long id = jdbc.sql("insert into payment(account, cents, status) values (?, ?, ?) returning id")
                .params(account, cents, status).query(Long.class).single();
        return new Payment(id, account, cents, status);
    }

    public long count() { return jdbc.sql("select count(*) from payment").query(Long.class).single(); }
}
