import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" LocksDemo.java
public class LocksDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            c.createStatement().execute("""
                    drop table if exists account;
                    create table account(id text primary key, balance bigint not null, version int not null default 0);
                    insert into account(id, balance) values ('acc-1', 100), ('acc-2', 100);
                    """);
        }
        forUpdate();
        nowait();
        optimistic();
        deadlock();
        lockQueue();
    }

    static Connection tx() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        c.setAutoCommit(false);
        return c;
    }

    static long ms(long start) { return (System.nanoTime() - start) / 1_000_000; }

    // ---------- SELECT FOR UPDATE: второй ждёт ----------
    static void forUpdate() throws Exception {
        try (Connection a = tx(); Connection b = tx(); Connection watch = DriverManager.getConnection(URL)) {
            a.createStatement().executeQuery("select balance from account where id = 'acc-1' for update");
            var waiter = CompletableFuture.supplyAsync(() -> {
                long t = System.nanoTime();
                try {
                    var rs = b.createStatement().executeQuery("select balance from account where id = 'acc-1' for update");
                    rs.next();
                    return "B got the row after " + ms(t) + " ms, balance " + rs.getLong(1);
                } catch (SQLException e) { return e.getMessage(); }
            });
            Thread.sleep(300);
            var rs = watch.createStatement().executeQuery("""
                    select wait_event_type, wait_event, left(query, 60) from pg_stat_activity
                    where wait_event_type = 'Lock' and datname = 'postgres'""");
            while (rs.next()) System.out.println("== waiting: " + rs.getString(1) + "/" + rs.getString(2) + " — " + rs.getString(3));
            a.createStatement().executeUpdate("update account set balance = balance - 30 where id = 'acc-1'");
            a.commit();
            System.out.println("== " + waiter.get());
            b.commit();
        }
    }

    // ---------- NOWAIT: не ждать, а сразу ошибка ----------
    static void nowait() throws Exception {
        try (Connection a = tx(); Connection b = tx()) {
            a.createStatement().executeQuery("select 1 from account where id = 'acc-1' for update");
            try {
                b.createStatement().executeQuery("select 1 from account where id = 'acc-1' for update nowait");
            } catch (SQLException e) {
                System.out.println("== FOR UPDATE NOWAIT: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse(""));
            }
            a.rollback(); b.rollback();
        }
    }

    // ---------- оптимистичная блокировка по версии ----------
    static void optimistic() throws Exception {
        try (Connection a = DriverManager.getConnection(URL); Connection b = DriverManager.getConnection(URL)) {
            var ra = a.createStatement().executeQuery("select balance, version from account where id = 'acc-2'"); ra.next();
            var rb = b.createStatement().executeQuery("select balance, version from account where id = 'acc-2'"); rb.next();
            int okA = a.createStatement().executeUpdate("update account set balance = " + (ra.getLong(1) - 30)
                    + ", version = version + 1 where id = 'acc-2' and version = " + ra.getInt(2));
            int okB = b.createStatement().executeUpdate("update account set balance = " + (rb.getLong(1) - 50)
                    + ", version = version + 1 where id = 'acc-2' and version = " + rb.getInt(2));
            System.out.println("== optimistic: A updated " + okA + " row, B updated " + okB + " rows (version changed) -> B retries");
        }
    }

    // ---------- взаимоблокировка: переводы навстречу друг другу ----------
    static void deadlock() throws Exception {
        try (Connection a = tx(); Connection b = tx()) {
            a.createStatement().executeUpdate("update account set balance = balance - 10 where id = 'acc-1'");  // A: acc-1
            b.createStatement().executeUpdate("update account set balance = balance - 10 where id = 'acc-2'");  // B: acc-2
            long t = System.nanoTime();
            var fa = CompletableFuture.supplyAsync(() -> step(a, "acc-2", "A"));                                 // A ждёт acc-2
            var fb = CompletableFuture.supplyAsync(() -> step(b, "acc-1", "B"));                                 // B ждёт acc-1
            String ra = fa.get(), rb = fb.get();
            System.out.println("== deadlock detected after " + ms(t) + " ms: " + ra + "; " + rb);
            a.rollback(); b.rollback();
        }
    }

    static String step(Connection c, String id, String who) {
        try {
            c.createStatement().executeUpdate("update account set balance = balance + 10 where id = '" + id + "'");
            return who + " ok";
        } catch (SQLException e) {
            return who + " " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("");
        }
    }

    // ---------- очередь блокировок: ALTER ждёт отчёт, а все ждут ALTER ----------
    static void lockQueue() throws Exception {
        try (Connection report = tx(); Connection ddl = tx(); Connection app = DriverManager.getConnection(URL);
             Connection watch = DriverManager.getConnection(URL)) {
            report.createStatement().executeQuery("select count(*) from account");          // Долгий отчёт держит ACCESS SHARE.
            var alter = CompletableFuture.runAsync(() -> {
                try { ddl.createStatement().execute("alter table account add column note text"); ddl.commit(); }
                catch (SQLException e) { throw new RuntimeException(e); }
            });
            Thread.sleep(300);
            app.createStatement().execute("set statement_timeout = '1s'");
            long t0 = System.nanoTime();
            var select = CompletableFuture.supplyAsync(() -> {
                try {
                    app.createStatement().executeQuery("select balance from account where id = 'acc-1'");  // Обычный запрос.
                    return "went through in " + ms(t0) + " ms";
                } catch (SQLException e) { return e.getSQLState() + " after " + ms(t0) + " ms"; }
            });
            Thread.sleep(300);
            var rs = watch.createStatement().executeQuery("""
                    select left(query, 50), wait_event from pg_stat_activity
                    where wait_event_type = 'Lock' and datname = 'postgres' order by query_start""");
            while (rs.next()) System.out.println("   waiting: " + rs.getString(1) + " (" + rs.getString(2) + ")");
            System.out.println("== plain SELECT behind waiting ALTER: " + select.get());
            report.commit();                                                                    // Отчёт закончился.
            alter.get();
            System.out.println("   report finished -> ALTER done");

            ddl.createStatement().execute("set lock_timeout = '200ms'");                       // Правильный DDL.
            report.createStatement().executeQuery("select count(*) from account");
            long t = System.nanoTime();
            try {
                ddl.createStatement().execute("alter table account drop column note");
            } catch (SQLException e) {
                ddl.rollback();
                System.out.println("== ALTER with lock_timeout 200ms: " + e.getSQLState() + " after " + ms(t) + " ms, others not blocked");
            }
            report.commit();
        }
    }
}
