package kb.data;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.h2.jdbc.JdbcConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcDemo {
    record Account(String id, long cents) {}

    public static void main(String[] args) throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:jdbc-demo;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setMaximumPoolSize(1); // Намеренно один слот для опыта с ожиданием.
        config.setMinimumIdle(1);
        config.setConnectionTimeout(300); // Миллисекунды ожидания свободного слота.
        config.setValidationTimeout(250);
        config.setPoolName("payment-pool");
        try (HikariDataSource pool = new HikariDataSource(config)) {
            poolCycle(pool);
            JdbcTemplate jdbc = new JdbcTemplate(pool);
            jdbc.execute("create table account (id varchar(80) primary key, cents bigint not null check(cents >= 0))");
            jdbc.update("insert into account(id, cents) values (?, ?)", "payer", 10_000L);
            jdbc.update("insert into account(id, cents) values (?, ?)", "merchant", 0L);
            parametersAndErrors(jdbc);
            transfers(pool, jdbc);
        }
    }

    static void poolCycle(HikariDataSource pool) throws SQLException {
        Connection first = pool.getConnection();
        JdbcConnection physical = first.unwrap(JdbcConnection.class); // Только для доказательства в H2.
        try (first) {
            var stats = pool.getHikariPoolMXBean();
            System.out.println("held active=" + stats.getActiveConnections() + ", idle=" + stats.getIdleConnections());
            try (Connection second = pool.getConnection()) {
                // Эта строка достижима только при получении второго соединения.
                try (var statement = second.createStatement()) { statement.execute("select 1"); }
                throw new IllegalStateException("one-slot pool unexpectedly supplied a second connection");
            } catch (SQLException expected) {
                System.out.println("second borrow=" + expected.getClass().getSimpleName());
            }
        }
        System.out.println("logical closed=" + first.isClosed() + ", physical closed=" + physical.isClosed());
        try (Connection next = pool.getConnection()) {
            System.out.println("new wrapper=" + (next != first)
                    + ", same physical=" + (next.unwrap(JdbcConnection.class) == physical));
        }
    }

    static void parametersAndErrors(JdbcTemplate jdbc) {
        var payer = jdbc.queryForObject("select id, cents from account where id = ?",
                (rs, rowNum) -> new Account(rs.getString("id"), rs.getLong("cents")), "payer");
        System.out.println("mapped=" + payer);
        String input = "payer' OR '1'='1";
        var matches = jdbc.query("select id, cents from account where id = ?",
                (rs, rowNum) -> new Account(rs.getString("id"), rs.getLong("cents")), input);
        System.out.println("parameter rows=" + matches.size()); // Вся строка — значение одного id.
        try {
            jdbc.queryForObject("select cents from account where id = ?", Long.class, "missing");
        } catch (DataAccessException expected) {
            System.out.println("missing row=" + expected.getClass().getSimpleName());
        }
        try {
            jdbc.update("insert into account(id, cents) values (?, ?)", "payer", 0L);
        } catch (DataAccessException expected) {
            System.out.println("duplicate=" + expected.getClass().getSimpleName());
        }
    }

    static void transfers(DataSource pool, JdbcTemplate jdbc) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            // Hikari по умолчанию выдаёт autoCommit=true: первый UPDATE фиксируется отдельно.
            debit(connection);
            throw new IllegalStateException("receipt failed"); // Ошибка до зачисления.
        } catch (IllegalStateException expected) {
            System.out.println("autoCommit failure: " + balances(jdbc));
        }
        reset(jdbc);
        try {
            transferInTransaction(pool, true);
        } catch (IllegalStateException expected) {
            System.out.println("rollback: " + balances(jdbc));
        }
        try (Connection next = pool.getConnection()) {
            System.out.println("after return autoCommit=" + next.getAutoCommit());
        }
        transferInTransaction(pool, false);
        System.out.println("commit: " + balances(jdbc));
    }

    static void transferInTransaction(DataSource pool, boolean fail) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false); // Обе записи используют одно соединение.
            try {
                debit(connection);
                if (fail) throw new IllegalStateException("receipt failed");
                credit(connection);
                connection.commit(); // Фиксируем обе записи после успеха.
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback(); // Отмена нужна до close, а не вместо него.
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure); // Сохраняем первичную причину.
                }
                throw failure;
            }
        } // Hikari вернёт свой стандартный autoCommit при возврате соединения.
    }

    static void debit(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "update account set cents = cents - ? where id = ? and cents >= ?")) {
            statement.setLong(1, 1_000L);
            statement.setString(2, "payer");
            statement.setLong(3, 1_000L); // Проверка остатка входит в тот же UPDATE.
            if (statement.executeUpdate() != 1) throw new IllegalStateException("payer missing or insufficient funds");
        }
    }

    static void credit(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("update account set cents = cents + ? where id = ?")) {
            statement.setLong(1, 1_000L);
            statement.setString(2, "merchant");
            if (statement.executeUpdate() != 1) throw new IllegalStateException("merchant missing");
        }
    }

    static String balances(JdbcTemplate jdbc) {
        long payer = jdbc.queryForObject("select cents from account where id = ?", Long.class, "payer");
        long merchant = jdbc.queryForObject("select cents from account where id = ?", Long.class, "merchant");
        return "payer=" + payer + ", merchant=" + merchant;
    }

    static void reset(JdbcTemplate jdbc) {
        jdbc.update("update account set cents = ? where id = ?", 10_000L, "payer");
        jdbc.update("update account set cents = ? where id = ?", 0L, "merchant");
    }
}
