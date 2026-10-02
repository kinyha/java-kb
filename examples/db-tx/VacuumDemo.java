import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" VacuumDemo.java
public class VacuumDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            bloat(c);
            hot(c);
            thresholds(c);
            freeze(c);
        }
    }

    // ---------- раздувание и что делает VACUUM ----------
    static void bloat(Connection c) throws SQLException {
        exec(c, """
                create extension if not exists pgstattuple;
                drop table if exists ledger;
                create table ledger(id bigint primary key, balance bigint, note text) with (autovacuum_enabled = false);
                insert into ledger select g, 100, repeat('x', 100) from generate_series(1, 100000) g;
                analyze ledger;
                """);
        report(c, "after insert of 100k rows");
        for (int i = 0; i < 3; i++) exec(c, "update ledger set balance = balance + 1");
        report(c, "after 3 full-table UPDATEs");
        exec(c, "vacuum ledger");
        report(c, "after VACUUM");
        for (int i = 0; i < 2; i++) { exec(c, "update ledger set balance = balance + 1"); exec(c, "vacuum ledger"); }
        report(c, "after 2 more UPDATE+VACUUM rounds");
        exec(c, "vacuum full ledger");
        report(c, "after VACUUM FULL");
    }

    static void report(Connection c, String title) throws SQLException {
        var rs = c.createStatement().executeQuery("""
                select pg_size_pretty(table_len), tuple_count, dead_tuple_count, round(free_percent)
                from pgstattuple('ledger')""");                    // Точный подсчёт по страницам.
        rs.next();
        System.out.printf("== %-34s size %-7s live %-7d dead %-7d free %s%%%n", title, rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getString(4));
    }

    // ---------- HOT: обновление без новых записей в индексах ----------
    static void hot(Connection c) throws SQLException {
        for (int fillfactor : new int[] {100, 90}) {
            for (boolean indexedColumn : new boolean[] {false, true}) {
                exec(c, "drop table if exists acct; create table acct(id bigint primary key, balance bigint, status text) "
                        + "with (fillfactor = " + fillfactor + ", autovacuum_enabled = false);"
                        + "create index on acct(status);"
                        + "insert into acct select g, 100, 'ACTIVE' from generate_series(1, 20000) g;");
                String col = indexedColumn ? "status = 'BLOCKED'" : "balance = balance - 1";
                exec(c, "update acct set " + col + " where id % 10 = 0");          // Каждая десятая строка.
                exec(c, "select pg_stat_force_next_flush()");
                var rs = c.createStatement().executeQuery("select n_tup_upd, n_tup_hot_upd from pg_stat_user_tables where relname = 'acct'");
                rs.next();
                System.out.printf("== HOT, fillfactor %d, update %-26s updated %d, HOT %d%n", fillfactor,
                        indexedColumn ? "indexed column (status):" : "non-indexed (balance):", rs.getLong(1), rs.getLong(2));
            }
        }
    }

    // ---------- когда autovacuum придёт сам ----------
    static void thresholds(Connection c) throws SQLException {
        var rs = c.createStatement().executeQuery("""
                select current_setting('autovacuum_vacuum_threshold'), current_setting('autovacuum_vacuum_scale_factor')""");
        rs.next();
        long base = Long.parseLong(rs.getString(1));
        double scale = Double.parseDouble(rs.getString(2));
        System.out.println("== autovacuum_vacuum_threshold=" + base + ", scale_factor=" + scale);
        for (long rows : new long[] {10_000, 1_000_000, 100_000_000})
            System.out.printf("   table of %,d rows: vacuum after ~%,d dead rows%n", rows, (long) (base + scale * rows));
    }

    // ---------- возраст транзакций и заморозка ----------
    static void freeze(Connection c) throws SQLException {
        var rs = c.createStatement().executeQuery("""
                select age(relfrozenxid), current_setting('autovacuum_freeze_max_age') from pg_class where relname = 'ledger'""");
        rs.next();
        System.out.println("== ledger relfrozenxid age " + rs.getLong(1) + " of autovacuum_freeze_max_age " + rs.getString(2));
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }
}
