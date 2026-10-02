import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Запуск: PostgreSQL 17 с max_prepared_transactions=10 и базами wallet и bank (см. README), затем
// java -cp "$(cat target/classpath.txt)" DistTxDemo.java
// Две базы — два «сервиса»: кошелёк клиента и счёт магазина в банке.
public class DistTxDemo {
    static final String WALLET = "jdbc:postgresql://localhost:55432/wallet?user=postgres&password=pw";
    static final String BANK = "jdbc:postgresql://localhost:55432/bank?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection w = DriverManager.getConnection(WALLET); Connection b = DriverManager.getConnection(BANK)) {
            // Хвосты прошлых запусков: prepared-транзакции переживают даже отключение клиента.
            for (var x : List.of(w, b)) for (var r = x.createStatement().executeQuery("select gid from pg_prepared_xacts where database = current_database()"); r.next(); )
                exec(x, "rollback prepared '" + r.getString(1) + "'");
            exec(w, "drop table if exists wallet; create table wallet(id text primary key, balance bigint check (balance >= 0)); insert into wallet values ('anna', 1000)");
            exec(b, "drop table if exists account; create table account(id text primary key, balance bigint, active boolean); insert into account values ('shop-7', 0, true), ('shop-9', 0, false)");
        }
        twoPhaseCommit();
        coordinatorCrash();
        saga("shop-7");
        saga("shop-9");
    }

    // ---------- 2PC: подготовить обе, затем закоммитить обе ----------
    static void twoPhaseCommit() throws Exception {
        try (Connection w = tx(WALLET); Connection b = tx(BANK)) {
            exec(w, "update wallet set balance = balance - 300 where id = 'anna'");
            exec(b, "update account set balance = balance + 300 where id = 'shop-7'");
            exec(w, "prepare transaction 'pay-1-wallet'");          // Фаза 1: каждая база обещает, что сможет закоммитить.
            exec(b, "prepare transaction 'pay-1-bank'");
            w.setAutoCommit(true); b.setAutoCommit(true);          // COMMIT PREPARED — вне транзакции.
            exec(w, "commit prepared 'pay-1-wallet'");              // Фаза 2: координатор говорит «коммит» обеим.
            exec(b, "commit prepared 'pay-1-bank'");
        }
        System.out.println("== 2PC: anna " + scalar(WALLET, "select balance from wallet where id = 'anna'")
                + ", shop-7 " + scalar(BANK, "select balance from account where id = 'shop-7'"));
    }

    // ---------- координатор упал между фазами: prepared держит блокировку ----------
    static void coordinatorCrash() throws Exception {
        Connection w = tx(WALLET);
        exec(w, "update wallet set balance = balance - 100 where id = 'anna'");
        exec(w, "prepare transaction 'pay-2-wallet'");
        w.close();                                                   // Координатор «упал» до фазы 2.
        System.out.println("== coordinator crashed after prepare: " + scalar(WALLET, "select count(*) from pg_prepared_xacts") + " prepared transaction left");
        try (Connection other = DriverManager.getConnection(WALLET)) {
            exec(other, "set lock_timeout = '2s'");
            try {
                exec(other, "update wallet set balance = balance + 50 where id = 'anna'");   // Чужой платёж той же клиентки.
            } catch (SQLException e) {
                System.out.println("   another payment of anna: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse(""));
            }
        }
        exec(DriverManager.getConnection(WALLET), "rollback prepared 'pay-2-wallet'");   // Решение принимает человек или восстановление координатора.
        System.out.println("   after manual ROLLBACK PREPARED anna = " + scalar(WALLET, "select balance from wallet where id = 'anna'"));
    }

    // ---------- сага: локальные транзакции и компенсация ----------
    static void saga(String shop) throws Exception {
        List<String> log = new ArrayList<>();
        // Шаг 1 — локальная транзакция кошелька, сразу видна всем.
        exec(DriverManager.getConnection(WALLET), "update wallet set balance = balance - 200 where id = 'anna'");
        log.add("wallet: -200 committed");
        String seenMeanwhile = "anna balance seen by others meanwhile: " + scalar(WALLET, "select balance from wallet where id = 'anna'");
        try (Connection b = DriverManager.getConnection(BANK)) {
            // Шаг 2 — зачисление в банке; неактивный счёт — бизнес-отказ.
            var ps = b.prepareStatement("update account set balance = balance + 200 where id = ? and active");
            ps.setString(1, shop);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("account " + shop + " is not active");
            log.add("bank: +200 committed");
        } catch (IllegalStateException e) {
            log.add("bank: rejected (" + e.getMessage() + ")");
            // Компенсация шага 1 — отдельная транзакция, не откат.
            exec(DriverManager.getConnection(WALLET), "update wallet set balance = balance + 200 where id = 'anna'");
            log.add("wallet: +200 compensation committed");
        }
        System.out.println("== saga to " + shop + ": " + String.join(" -> ", log));
        System.out.println("   " + seenMeanwhile + ", final anna " + scalar(WALLET, "select balance from wallet where id = 'anna'"));
    }

    static Connection tx(String url) throws SQLException { Connection c = DriverManager.getConnection(url); c.setAutoCommit(false); return c; }
    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }
    static long scalar(String url, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url)) { var rs = c.createStatement().executeQuery(sql); rs.next(); return rs.getLong(1); }
    }
}
