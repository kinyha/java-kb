import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" MvccDemo.java
public class MvccDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                    create extension if not exists pageinspect;
                    drop table if exists wallet;
                    create table wallet(id text primary key, balance bigint) with (autovacuum_enabled = false);
                    insert into wallet values ('w-1', 100);
                    """);
            System.out.println("== after insert");
            print(c, "select ctid, xmin, xmax, * from wallet");
            exec(c, "update wallet set balance = 70 where id = 'w-1'");
            System.out.println("== after update: the table shows one row...");
            print(c, "select ctid, xmin, xmax, * from wallet");
            System.out.println("== ...but the page holds two versions");
            print(c, "select lp, t_xmin, t_xmax, t_ctid from heap_page_items(get_raw_page('wallet', 0))");
        }

        try (Connection reader = DriverManager.getConnection(URL); Connection writer = DriverManager.getConnection(URL);
             Connection c = DriverManager.getConnection(URL)) {
            reader.setAutoCommit(false);
            reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            System.out.println("== reader snapshot (xmin:xmax:in-progress)");
            print(reader, "select pg_current_snapshot() as snapshot, balance from wallet");
            long t = System.nanoTime();
            exec(writer, "update wallet set balance = 40 where id = 'w-1'");     // Пишет, пока читатель открыт.
            System.out.printf("== writer updated while reader is open, waited %d ms%n", (System.nanoTime() - t) / 1_000_000);
            System.out.println("== reader still sees its version; a new query sees the new one");
            print(reader, "select ctid, xmin, balance from wallet");
            print(c, "select ctid, xmin, balance from wallet");

            System.out.println("== VACUUM while the reader's snapshot is open");
            vacuum(c);
            reader.commit();
            System.out.println("== VACUUM after the reader finished");
            vacuum(c);
            print(c, "select lp, t_xmin, t_xmax, t_ctid from heap_page_items(get_raw_page('wallet', 0))");
        }
    }

    static void vacuum(Connection c) throws SQLException {
        var st = c.createStatement();
        st.execute("vacuum (verbose) wallet");
        for (var w = st.getWarnings(); w != null; w = w.getNextWarning()) {
            if (!w.getMessage().contains("public.wallet")) continue;          // Пропустить TOAST-таблицу.
            w.getMessage().lines().filter(l -> l.contains("tuples:") || l.contains("removable cutoff"))
                    .forEach(l -> System.out.println("   " + l.trim()));
        }
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }

    static void print(Connection c, String sql) throws SQLException {
        ResultSet rs = c.createStatement().executeQuery(sql);
        int n = rs.getMetaData().getColumnCount();
        StringBuilder h = new StringBuilder("   ");
        for (int i = 1; i <= n; i++) h.append(String.format("%-16s", rs.getMetaData().getColumnName(i)));
        System.out.println(h.toString().stripTrailing());
        while (rs.next()) {
            StringBuilder r = new StringBuilder("   ");
            for (int i = 1; i <= n; i++) r.append(String.format("%-16s", rs.getString(i)));
            System.out.println(r.toString().stripTrailing());
        }
    }
}
