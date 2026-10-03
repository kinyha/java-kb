import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" WalletDemo.java
public class WalletDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    interface Transfer { void run(Connection c, String from, String to, long amount) throws SQLException; }

    public static void main(String[] args) throws Exception {
        System.out.println("== 2000 concurrent random transfers between 4 accounts of 10000.00, 16 threads");
        race("read, then write", WalletDemo::naive);
        race("conditional update", WalletDemo::conditional);

        System.out.println("== 200 transfers anna<->boris in both directions, 16 threads");
        deadlocks("lock from, then to", false);
        deadlocks("lock in id order", true);

        System.out.println("== ledger: balance is a sum of entries");
        ledger();
    }

    static void reset(Connection c) throws SQLException {
        c.createStatement().execute("""
            drop table if exists entry, account;
            create table account(id text primary key, balance bigint not null check (balance >= 0));
            insert into account select 'acc-' || g, 1000000 from generate_series(1, 4) g;
            insert into account values ('anna', 1000000), ('boris', 1000000);
            create table entry(transfer_id bigint, account text, amount bigint, at timestamptz default clock_timestamp());
            """);
    }

    // Наивно: прочитать балансы, проверить в Java, записать новые значения.
    static void naive(Connection c, String from, String to, long amount) throws SQLException {
        long a = balance(c, from), b = balance(c, to);
        if (a < amount) return;
        set(c, from, a - amount);
        set(c, to, b + amount);
    }

    // Условный UPDATE: проверка и изменение — одна операция под блокировкой строки.
    static void conditional(Connection c, String from, String to, long amount) throws SQLException {
        var debit = c.prepareStatement("update account set balance = balance - ? where id = ? and balance >= ?");
        debit.setLong(1, amount); debit.setString(2, from); debit.setLong(3, amount);
        if (debit.executeUpdate() == 0) { c.rollback(); return; }        // недостаточно средств
        var credit = c.prepareStatement("update account set balance = balance + ? where id = ?");
        credit.setLong(1, amount); credit.setString(2, to);
        credit.executeUpdate();
    }

    static void race(String name, Transfer t) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) { reset(c); }
        AtomicInteger failed = new AtomicInteger();
        run(2000, i -> {
            Random r = new Random(i);
            String from = "acc-" + (1 + r.nextInt(4)), to = "acc-" + (1 + r.nextInt(4));
            if (from.equals(to)) return;
            try (Connection c = DriverManager.getConnection(URL)) {
                c.setAutoCommit(false);
                t.run(c, from, to, 100 + r.nextInt(5000));
                c.commit();
            } catch (SQLException e) { failed.incrementAndGet(); }
        });
        System.out.printf("   %-20s total %s (expected 4000000), failed %d%n", name, scalar("select sum(balance) from account where id like 'acc-%'"), failed.get());
    }

    static void deadlocks(String name, boolean ordered) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) { reset(c); }
        AtomicInteger deadlocks = new AtomicInteger();
        run(200, i -> {
            String from = i % 2 == 0 ? "anna" : "boris", to = i % 2 == 0 ? "boris" : "anna";
            try (Connection c = DriverManager.getConnection(URL)) {
                c.setAutoCommit(false);
                List<String> order = ordered && from.compareTo(to) > 0 ? List.of(to, from) : List.of(from, to);
                for (String id : order) {                                       // блокируем строки
                    var p = c.prepareStatement("select balance from account where id = ? for update");
                    p.setString(1, id); p.executeQuery();
                }
                conditional(c, from, to, 1);
                c.commit();
            } catch (SQLException e) {
                if ("40P01".equals(e.getSQLState())) deadlocks.incrementAndGet();   // deadlock_detected
            }
        });
        System.out.printf("   %-20s deadlocks %d of 200, total %s%n", name, deadlocks.get(), scalar("select sum(balance) from account where id in ('anna','boris')"));
    }

    static void ledger() throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            reset(c);
            c.setAutoCommit(false);
            long[][] transfers = {{1, 30000}, {2, 12000}, {3, 5000}};
            String[][] pairs = {{"anna", "boris"}, {"boris", "anna"}, {"anna", "boris"}};
            Timestamp afterSecond = null;
            for (int i = 0; i < 3; i++) {
                conditional(c, pairs[i][0], pairs[i][1], transfers[i][1]);
                // Две проводки на перевод: минус у отправителя, плюс у получателя. Сумма — ноль.
                var e = c.prepareStatement("insert into entry(transfer_id, account, amount) values (?, ?, ?), (?, ?, ?)");
                e.setLong(1, transfers[i][0]); e.setString(2, pairs[i][0]); e.setLong(3, -transfers[i][1]);
                e.setLong(4, transfers[i][0]); e.setString(5, pairs[i][1]); e.setLong(6, transfers[i][1]);
                e.executeUpdate();
                c.commit();
                if (i == 1) { Thread.sleep(5); afterSecond = new Timestamp(System.currentTimeMillis()); Thread.sleep(5); }
            }
            ResultSet r = c.createStatement().executeQuery("""
                select a.id, a.balance, 1000000 + coalesce(sum(e.amount), 0) as from_entries
                from account a left join entry e on e.account = a.id
                where a.id in ('anna', 'boris') group by a.id, a.balance order by a.id""");
            while (r.next()) System.out.printf("   %-6s stored %d, from entries %d%n", r.getString(1), r.getLong(2), r.getLong(3));
            System.out.println("   sum of all entries: " + scalar("select sum(amount) from entry"));
            var asOf = c.prepareStatement("select 1000000 + coalesce(sum(amount), 0) from entry where account = 'anna' and at <= ?");
            asOf.setTimestamp(1, afterSecond);
            r = asOf.executeQuery(); r.next();
            System.out.println("   anna as of after transfer 2: " + r.getLong(1));
            c.commit();
        }
    }

    interface Task { void run(int i) throws Exception; }

    static void run(int n, Task task) throws Exception {
        var pool = Executors.newFixedThreadPool(16);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) { int k = i; fs.add(pool.submit(() -> { task.run(k); return null; })); }
        for (var f : fs) f.get();
        pool.shutdown();
    }

    static long balance(Connection c, String id) throws SQLException {
        var p = c.prepareStatement("select balance from account where id = ?");
        p.setString(1, id);
        ResultSet r = p.executeQuery(); r.next(); return r.getLong(1);
    }

    static void set(Connection c, String id, long v) throws SQLException {
        var p = c.prepareStatement("update account set balance = ? where id = ?");
        p.setLong(1, v); p.setString(2, id); p.executeUpdate();
    }

    static String scalar(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); ResultSet r = c.createStatement().executeQuery(sql)) {
            r.next(); return r.getString(1);
        }
    }
}
