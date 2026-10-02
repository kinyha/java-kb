import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: docker run -d --name kb-pg -e POSTGRES_PASSWORD=pw -p 55432:5432 postgres:17-alpine
//         java -cp "$(cat target/classpath.txt)" IsolationDemo.java
// Две транзакции в двух соединениях; шаги идут строго по очереди, как в комментариях.
public class IsolationDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection admin = DriverManager.getConnection(URL)) {
            admin.createStatement().execute("""
                    drop table if exists account, payout_approval;
                    create table account(id text primary key, balance bigint not null);
                    create table payout_approval(payout text, approver text, on_duty boolean);
                    """);
        }
        nonRepeatableRead(Connection.TRANSACTION_READ_COMMITTED);
        nonRepeatableRead(Connection.TRANSACTION_REPEATABLE_READ);
        lostUpdate(Connection.TRANSACTION_READ_COMMITTED);
        lostUpdate(Connection.TRANSACTION_REPEATABLE_READ);
        atomicUpdate();
        phantom(Connection.TRANSACTION_READ_COMMITTED);
        phantom(Connection.TRANSACTION_REPEATABLE_READ);
        writeSkew(Connection.TRANSACTION_REPEATABLE_READ);
        writeSkew(Connection.TRANSACTION_SERIALIZABLE);
        defaults();
    }

    static Connection tx(int level) throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        c.setAutoCommit(false);
        c.setTransactionIsolation(level);
        return c;
    }

    static String name(int level) {
        return switch (level) {
            case Connection.TRANSACTION_READ_COMMITTED -> "READ COMMITTED";
            case Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE READ";
            default -> "SERIALIZABLE";
        };
    }

    static long balance(Connection c) throws SQLException {
        var rs = c.createStatement().executeQuery("select balance from account where id = 'acc-1'");
        rs.next();
        return rs.getLong(1);
    }

    static void reset(long balance) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) {
            c.createStatement().execute("delete from account; insert into account values ('acc-1', " + balance + ")");
        }
    }

    // ---------- неповторяемое чтение ----------
    static void nonRepeatableRead(int level) throws Exception {
        reset(100);
        try (Connection a = tx(level); Connection b = tx(level)) {
            long first = balance(a);                                         // A читает.
            b.createStatement().executeUpdate("update account set balance = 40 where id = 'acc-1'");
            b.commit();                                                      // B меняет и коммитит.
            long second = balance(a);                                        // A читает снова.
            a.commit();
            System.out.printf("== non-repeatable read, %s: A read %d, then %d%n", name(level), first, second);
        }
    }

    // ---------- потерянное обновление: прочитать, посчитать, записать ----------
    static void lostUpdate(int level) throws Exception {
        reset(100);
        try (Connection a = tx(level); Connection b = tx(level)) {
            long ba = balance(a), bb = balance(b);                           // Оба прочитали 100.
            a.createStatement().executeUpdate("update account set balance = " + (ba - 30) + " where id = 'acc-1'");
            a.commit();                                                      // A списал 30.
            String result;
            try {
                b.createStatement().executeUpdate("update account set balance = " + (bb - 50) + " where id = 'acc-1'");
                b.commit();                                                  // B списал 50 от своего снимка.
                result = "B committed";
            } catch (SQLException e) {
                b.rollback();
                result = "B failed: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("");
            }
            try (Connection c = DriverManager.getConnection(URL)) {
                System.out.printf("== lost update, %s: %s; balance %d (expected 20)%n", name(level), result, balance(c));
            }
        }
    }

    static void atomicUpdate() throws Exception {
        reset(100);
        try (Connection a = tx(Connection.TRANSACTION_READ_COMMITTED); Connection b = tx(Connection.TRANSACTION_READ_COMMITTED)) {
            a.createStatement().executeUpdate("update account set balance = balance - 30 where id = 'acc-1'");
            var t = Thread.ofPlatform().start(() -> {                        // B ждёт блокировку строки.
                try { b.createStatement().executeUpdate("update account set balance = balance - 50 where id = 'acc-1'"); b.commit(); }
                catch (SQLException e) { throw new RuntimeException(e); }
            });
            Thread.sleep(300);
            a.commit();
            t.join();
            try (Connection c = DriverManager.getConnection(URL)) {
                System.out.printf("== atomic update balance = balance - x, READ COMMITTED: balance %d%n", balance(c));
            }
        }
    }

    // ---------- фантом: новая строка подходит под условие ----------
    static void phantom(int level) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            c.createStatement().execute("delete from account; insert into account values ('acc-1', 100), ('acc-2', 200)");
        }
        try (Connection a = tx(level); Connection b = tx(level)) {
            var q = "select count(*), sum(balance) from account where balance >= 100";
            var r1 = a.createStatement().executeQuery(q); r1.next();
            String first = r1.getLong(1) + " accounts / " + r1.getLong(2);
            b.createStatement().executeUpdate("insert into account values ('acc-3', 500)");
            b.commit();
            var r2 = a.createStatement().executeQuery(q); r2.next();
            String second = r2.getLong(1) + " accounts / " + r2.getLong(2);
            a.commit();
            System.out.printf("== phantom, %s: report saw %s, then %s%n", name(level), first, second);
        }
    }

    // ---------- write skew: две проверки, два решения, нарушенный инвариант ----------
    // Правило: выплату должен одобрить хотя бы один дежурный, который остаётся на смене.
    static void writeSkew(int level) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            c.createStatement().execute("""
                    delete from payout_approval;
                    insert into payout_approval values ('po-1', 'anna', true), ('po-1', 'boris', true);
                    """);
        }
        try (Connection a = tx(level); Connection b = tx(level)) {
            String onDuty = "select count(*) from payout_approval where payout = 'po-1' and on_duty";
            var ra = a.createStatement().executeQuery(onDuty); ra.next();
            var rb = b.createStatement().executeQuery(onDuty); rb.next();
            // Оба видят двоих дежурных и решают, что могут уйти со смены.
            if (ra.getLong(1) > 1) a.createStatement().executeUpdate("update payout_approval set on_duty = false where approver = 'anna'");
            if (rb.getLong(1) > 1) b.createStatement().executeUpdate("update payout_approval set on_duty = false where approver = 'boris'");
            String result;
            a.commit();
            try {
                b.commit();
                result = "both committed";
            } catch (SQLException e) {
                result = "B failed: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("");
            }
            try (Connection c = DriverManager.getConnection(URL)) {
                var r = c.createStatement().executeQuery(onDuty); r.next();
                System.out.printf("== write skew, %s: %s; on duty now %d%n", name(level), result, r.getLong(1));
            }
        }
    }

    static void defaults() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) {
            var rs = c.createStatement().executeQuery("show default_transaction_isolation"); rs.next();
            var v = c.createStatement().executeQuery("select version()"); v.next();
            System.out.println("== default isolation: " + rs.getString(1) + " (" + v.getString(1).split(" on ")[0] + ")");
        }
    }
}
