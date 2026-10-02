package kb.payments;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import kb.payments.Payments.Payment;
import kb.payments.Payments.PaymentRepository;

/** Настоящая реализация: таблица payment с первичным ключом id. */
public class JdbcPaymentRepository implements PaymentRepository {
    private final Connection db;

    public JdbcPaymentRepository(Connection db) { this.db = db; }

    public Optional<Payment> findById(String id) {
        try (var ps = db.prepareStatement("select id, amount, status from payment where id = ?")) {
            ps.setString(1, id);
            var rs = ps.executeQuery();
            return rs.next() ? Optional.of(new Payment(rs.getString(1), rs.getLong(2), rs.getString(3))) : Optional.empty();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public boolean existsById(String id) { return findById(id).isPresent(); }

    public void save(Payment p) {
        try (var ps = db.prepareStatement("insert into payment(id, amount, status) values (?, ?, ?)")) {
            ps.setString(1, p.id());
            ps.setLong(2, p.amount());
            ps.setString(3, p.status());
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalStateException("payment " + p.id() + " already exists", e);
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
}
