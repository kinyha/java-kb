import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" QueryPlanDemo.java
public class QueryPlanDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";
    static Connection c;

    public static void main(String[] args) throws Exception {
        c = DriverManager.getConnection(URL);
        exec("""
                set max_parallel_workers_per_gather = 0;
                drop table if exists payment, merchant;
                create table merchant(id int primary key, name text, country text, city text);
                insert into merchant select g, 'shop-' || g,
                       case when g % 10 < 8 then 'RU' else 'KZ' end,
                       case when g % 10 < 8 then 'Moscow' else 'Almaty' end
                from generate_series(1, 10000) g;
                create table payment(id bigint primary key, merchant_id int, amount bigint, created_at timestamptz);
                insert into payment select g, g % 10000 + 1, g % 5000, timestamptz '2026-01-01' + (g || ' seconds')::interval * 30
                from generate_series(1, 1000000) g;
                create index on payment(merchant_id);
                """);
        exec("analyze merchant");
        exec("analyze payment");

        System.out.println("== join: one merchant's payments");
        plan("select p.id, p.amount from merchant m join payment p on p.merchant_id = m.id where m.name = 'shop-42'", 6);
        System.out.println("== join: all merchants of a city, sum of payments");
        plan("select m.city, sum(p.amount) from merchant m join payment p on p.merchant_id = m.id where m.city = 'Almaty' group by m.city", 7);
        exec("set enable_hashjoin = off; set enable_mergejoin = off");
        System.out.println("== same query, hash and merge join disabled");
        plan("select m.city, sum(p.amount) from merchant m join payment p on p.merchant_id = m.id where m.city = 'Almaty' group by m.city", 7);
        exec("reset enable_hashjoin; reset enable_mergejoin");

        System.out.println("== correlated columns: country = 'KZ' and city = 'Almaty'");
        plan("select count(*) from merchant where country = 'KZ' and city = 'Almaty'", 2);
        exec("create statistics merchant_geo (dependencies) on country, city from merchant");
        exec("analyze merchant");
        System.out.println("== after CREATE STATISTICS (dependencies)");
        plan("select count(*) from merchant where country = 'KZ' and city = 'Almaty'", 2);

        for (String wm : new String[] {"4MB", "64MB"}) {
            exec("set work_mem = '" + wm + "'");
            System.out.println("== sort 1M payments by amount, work_mem = " + wm);
            plan("select id from payment order by amount, created_at", 3);
        }
        c.close();
    }

    static void plan(String sql, int lines) throws SQLException {
        var rs = c.createStatement().executeQuery("explain (analyze, costs on, timing off, summary on) " + sql);
        int n = 0;
        while (rs.next()) {
            String l = rs.getString(1);
            if (l.startsWith("Planning")) continue;
            if (n++ < lines || l.startsWith("Execution")) System.out.println("   " + l.replaceAll("cost=[0-9.]+\\.\\.", "cost=..."));
        }
    }

    static void exec(String sql) throws SQLException { c.createStatement().execute(sql); }
}
