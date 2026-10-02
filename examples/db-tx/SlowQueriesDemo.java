import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 с pg_stat_statements (см. README), затем
// java -cp "$(cat target/classpath.txt)" SlowQueriesDemo.java
public class SlowQueriesDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                    create extension if not exists pg_stat_statements;
                    drop table if exists payment, merchant, audit;
                    create table merchant(id int primary key, name text);
                    insert into merchant select g, 'shop-' || g from generate_series(1, 1000) g;
                    create table payment(id bigint primary key, merchant_id int, amount bigint);
                    insert into payment select g, g % 1000 + 1, g % 5000 from generate_series(1, 200000) g;
                    create index on payment(merchant_id);
                    create table audit(id bigint, event text);
                    """);
            exec(c, "analyze");
            exec(c, "select pg_stat_statements_reset()");

            // ---------- N+1: магазины, затем платежи каждого отдельным запросом ----------
            long t = System.nanoTime();
            var shops = c.createStatement().executeQuery("select id from merchant order by id limit 200");
            var ps = c.prepareStatement("select sum(amount) from payment where merchant_id = ?");
            long total = 0;
            while (shops.next()) {
                ps.setInt(1, shops.getInt(1));
                var r = ps.executeQuery(); r.next(); total += r.getLong(1);
            }
            System.out.printf("== N+1: 1 + 200 queries, %d ms%n", (System.nanoTime() - t) / 1_000_000);
            t = System.nanoTime();
            var one = c.createStatement().executeQuery("""
                    select m.id, sum(p.amount) from merchant m join payment p on p.merchant_id = m.id
                    where m.id <= 200 group by m.id""");
            while (one.next()) total -= one.getLong(2);
            System.out.printf("== one join query, %d ms (same totals: %b)%n", (System.nanoTime() - t) / 1_000_000, total == 0);
        }

        // ---------- запись: по строке, batch и batch с переписыванием ----------
        for (String mode : new String[] {"row by row", "addBatch", "addBatch + reWriteBatchedInserts"}) {
            try (Connection c = DriverManager.getConnection(URL + (mode.contains("reWrite") ? "&reWriteBatchedInserts=true" : ""))) {
                exec(c, "truncate audit");
                c.setAutoCommit(false);
                var ps = c.prepareStatement("insert into audit values (?, ?)");
                long t = System.nanoTime();
                for (int i = 0; i < 20_000; i++) {
                    ps.setLong(1, i);
                    ps.setString(2, "payment captured");
                    if (mode.equals("row by row")) ps.executeUpdate(); else ps.addBatch();
                }
                if (!mode.equals("row by row")) ps.executeBatch();
                c.commit();
                System.out.printf("== insert 20k rows, %-33s %d ms%n", mode + ":", (System.nanoTime() - t) / 1_000_000);
            }
        }

        try (Connection c = DriverManager.getConnection(URL)) {
            System.out.println("== pg_stat_statements, top by total time");
            var rs = c.createStatement().executeQuery("""
                    select calls, round(total_exec_time::numeric, 1) total_ms, round(mean_exec_time::numeric, 3) mean_ms, rows,
                           left(regexp_replace(query, '\\s+', ' ', 'g'), 60) q
                    from pg_stat_statements where query not like '%pg_stat%' and query not like 'truncate%'
                    order by total_exec_time desc limit 6""");
            System.out.printf("   %6s %9s %8s %7s  %s%n", "calls", "total_ms", "mean_ms", "rows", "query");
            while (rs.next()) System.out.printf("   %6d %9s %8s %7d  %s%n", rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5));
        }
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }
}
