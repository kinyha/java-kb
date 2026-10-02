import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" PartitionDemo.java
public class PartitionDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";
    static Connection c;

    public static void main(String[] args) throws Exception {
        c = DriverManager.getConnection(URL);
        exec("""
                set max_parallel_workers_per_gather = 0;
                drop table if exists ledger, ledger_flat;
                create table ledger(id bigint, created_at timestamptz not null, account int, amount bigint)
                    partition by range (created_at);
                """);
        for (int m = 1; m <= 12; m++) {
            String from = String.format("2026-%02d-01", m), to = m == 12 ? "2027-01-01" : String.format("2026-%02d-01", m + 1);
            exec("create table ledger_2026_" + String.format("%02d", m) + " partition of ledger for values from ('" + from + "') to ('" + to + "')");
        }
        exec("""
                create index on ledger(account, created_at);
                insert into ledger select g, timestamptz '2026-01-01' + (g || ' seconds')::interval * 10, g % 5000, g % 1000
                from generate_series(1, 3000000) g;
                create table ledger_flat as select * from ledger;
                create index on ledger_flat(account, created_at);
                """);
        exec("analyze ledger");
        exec("analyze ledger_flat");

        System.out.println("== one month for one account");
        plan("select sum(amount) from ledger where account = 42 and created_at >= '2026-03-01' and created_at < '2026-04-01'");
        System.out.println("== no partition key in WHERE");
        plan("select sum(amount) from ledger where account = 42");

        System.out.println("== removing January");
        long t = System.nanoTime();
        int deleted = c.createStatement().executeUpdate("delete from ledger_flat where created_at < '2026-02-01'");
        System.out.printf("   DELETE from flat table: %d rows, %d ms (and dead rows left for VACUUM)%n", deleted, (System.nanoTime() - t) / 1_000_000);
        t = System.nanoTime();
        exec("alter table ledger detach partition ledger_2026_01");
        exec("drop table ledger_2026_01");
        System.out.printf("   DETACH + DROP partition: %d ms%n", (System.nanoTime() - t) / 1_000_000);

        System.out.println("== unique constraint without the partition key");
        for (String u : new String[] {"alter table ledger add constraint ledger_id_uq unique (id)",
                "alter table ledger add constraint ledger_id_ts_uq unique (id, created_at)"}) {
            try { exec(u); System.out.println("   " + u.substring(u.indexOf("unique")) + " -> ok"); }
            catch (SQLException e) { System.out.println("   " + u.substring(u.indexOf("unique")) + " -> " + e.getMessage().lines().findFirst().orElse("")); }
        }
        System.out.println("== insert outside any partition");
        try { exec("insert into ledger values (1, '2027-02-01', 1, 1)"); }
        catch (SQLException e) { System.out.println("   " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("")); }
        c.close();
    }

    static void plan(String sql) throws SQLException {
        var rs = c.createStatement().executeQuery("explain (analyze, costs off, timing off, summary on) " + sql);
        int scans = 0;
        while (rs.next()) {
            String l = rs.getString(1).strip();
            if (l.contains("Scan on") && !l.contains("Bitmap Index")) {            // Один узел на секцию.
                scans++;
                if (scans == 1) System.out.println("   " + l.replace("->  ", ""));
            }
            if (l.startsWith("Execution")) System.out.println("   partitions scanned: " + scans + " of 12; " + l);
        }
    }

    static void exec(String sql) throws SQLException { c.createStatement().execute(sql); }
}
