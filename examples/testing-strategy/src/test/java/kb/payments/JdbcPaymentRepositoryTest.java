package kb.payments;

import java.sql.DriverManager;
import java.util.UUID;
import kb.payments.Payments.PaymentRepository;

class JdbcPaymentRepositoryTest extends PaymentRepositoryContract {
    PaymentRepository create() throws Exception {
        var db = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());   // Своя база на тест.
        db.createStatement().execute("create table payment(id varchar primary key, amount bigint, status varchar)");
        return new JdbcPaymentRepository(db);
    }
}
