import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" IndexDemo.java
public class IndexDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";
    static Connection c;

    public static void main(String[] args) throws Exception {
        c = DriverManager.getConnection(URL);
        exec("""
                set max_parallel_workers_per_gather = 0;
                drop table if exists pay;
                create table pay(id bigint primary key, merchant int, created_at timestamptz, status text, amount bigint, email text);
                insert into pay
                select g, g % 1000, timestamptz '2026-01-01' + (g || ' seconds')::interval * 25,
                       case when g % 20 = 0 then 'FAILED' else 'CAPTURED' end, g % 10000, 'user' || (g % 50000) || '@mail.example'
                from generate_series(1, 1000000) g;
                """);
        exec("vacuum analyze pay");                          // VACUUM — отдельной командой.
        String q = "select id, amount from pay where merchant = 42 and created_at >= '2026-09-01' order by created_at desc limit 20";

        System.out.println("== no index");
        plan(q);
        exec("create index pay_created_merchant on pay(created_at, merchant)");
        System.out.println("== index (created_at, merchant)");
        plan(q);
        exec("drop index pay_created_merchant; create index pay_merchant_created on pay(merchant, created_at)");
        System.out.println("== index (merchant, created_at)");
        plan(q);
        exec("drop index pay_merchant_created; create index pay_merchant_created_cov on pay(merchant, created_at) include (amount)");
        exec("vacuum pay");
        System.out.println("== covering index (merchant, created_at) include (amount)");
        plan("select created_at, amount from pay where merchant = 42 and created_at >= '2026-09-01' order by created_at desc limit 20");

        exec("create index on pay(status)");
        System.out.println("== status = 'FAILED' (5% of rows)");
        plan("select sum(amount) from pay where status = 'FAILED'");
        System.out.println("== status = 'CAPTURED' (95% of rows)");
        plan("select sum(amount) from pay where status = 'CAPTURED'");

        exec("create index on pay(email)");
        System.out.println("== email = ...");
        plan("select id from pay where email = 'user777@mail.example'");
        System.out.println("== lower(email) = ...");
        plan("select id from pay where lower(email) = 'user777@mail.example'");
        exec("create index on pay(lower(email))");
        System.out.println("== lower(email) = ... with index on lower(email)");
        plan("select id from pay where lower(email) = 'user777@mail.example'");
        System.out.println("== email like '%777@mail.example'");
        plan("select id from pay where email like '%777@mail.example'");

        writes();
        c.close();
    }

    static void plan(String sql) throws SQLException {
        var rs = c.createStatement().executeQuery("explain (analyze, buffers, costs off, timing off, summary on) " + sql);
        boolean buffersShown = false;
        boolean planning = false;
        while (rs.next()) {
            String l = rs.getString(1);
            if (l.startsWith("Planning")) planning = true;
            if (planning && !l.startsWith("Execution")) continue;               // Строки планирования не нужны.
            boolean show = l.contains("Scan") || l.contains("Execution Time") || l.contains("Heap Fetches")
                    || l.contains("Index Cond") || l.contains("Filter:") || l.contains("Rows Removed");
            if (l.contains("Buffers") && !buffersShown) { show = true; buffersShown = true; }   // Итог по всему запросу.
            if (show) System.out.println("   " + l.strip());
        }
    }

    // ---------- цена индексов на запись ----------
    static void writes() throws SQLException {
        exec("drop table if exists w; create table w(id bigint primary key, merchant int, created_at timestamptz, status text, amount bigint, email text)");
        for (int extra : new int[] {0, 4}) {
            if (extra == 4) exec("create index on w(merchant); create index on w(created_at); create index on w(status); create index on w(email)");
            exec("truncate w");
            long t = System.nanoTime();
            exec("insert into w select g, g % 1000, now(), 'CAPTURED', g, 'u' || g || '@m.example' from generate_series(1, 200000) g");
            System.out.printf("== insert 200k rows with %d extra indexes: %d ms%n", extra, (System.nanoTime() - t) / 1_000_000);
        }
    }

    static void exec(String sql) throws SQLException { c.createStatement().execute(sql); }
}
