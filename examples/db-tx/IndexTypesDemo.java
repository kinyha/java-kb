import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" IndexTypesDemo.java
public class IndexTypesDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";
    static Connection c;

    public static void main(String[] args) throws Exception {
        c = DriverManager.getConnection(URL);
        exec("""
                set max_parallel_workers_per_gather = 0;
                create extension if not exists pg_trgm;
                create extension if not exists btree_gist;
                drop table if exists ev, slot;
                create table ev(id bigint, created_at timestamptz, status text, email text, attrs jsonb);
                insert into ev select g, timestamptz '2026-01-01' + (g || ' seconds')::interval * 10,
                    case when g % 100 = 0 then 'FAILED' else 'CAPTURED' end,
                    'user' || (g % 50000) || '@mail.example',
                    jsonb_build_object('country', case when g % 10 = 0 then 'KZ' else 'RU' end, 'channel', 'app', 'risk', g % 7)
                from generate_series(1, 1000000) g;
                """);
        exec("vacuum analyze ev");

        System.out.println("== jsonb containment attrs @> {country: KZ, risk: 3}");
        String jq = "select count(*) from ev where attrs @> '{\"country\": \"KZ\", \"risk\": 3}'";
        plan(jq);
        exec("create index ev_attrs_gin on ev using gin (attrs jsonb_path_ops)");
        plan(jq);

        System.out.println("== substring email like '%r777@%'");
        String lq = "select count(*) from ev where email like '%r777@%'";
        plan(lq);
        exec("create index ev_email_trgm on ev using gin (email gin_trgm_ops)");
        plan(lq);

        System.out.println("== time range on append-only table: btree vs brin");
        exec("create index ev_created_btree on ev(created_at)");
        exec("create index ev_created_brin on ev using brin (created_at)");
        String tq = "select count(*) from ev where created_at >= '2026-03-01' and created_at < '2026-03-02'";
        exec("drop index ev_created_btree");
        plan(tq);

        System.out.println("== partial index for rare status");
        exec("create index ev_failed_partial on ev(created_at) where status = 'FAILED'");
        plan("select count(*) from ev where status = 'FAILED' and created_at >= '2026-03-01'");
        exec("create index ev_status_full on ev(status, created_at)");
        plan("select count(*) from ev where status = 'FAILED' and created_at >= '2026-03-01'");

        System.out.println("== index sizes");
        var rs = c.createStatement().executeQuery("""
                select indexrelname, pg_size_pretty(pg_relation_size(indexrelid)) from pg_stat_user_indexes
                where relname = 'ev' order by pg_relation_size(indexrelid) desc""");
        while (rs.next()) System.out.printf("   %-20s %s%n", rs.getString(1), rs.getString(2));
        exec("create index ev_created_btree_tmp on ev(created_at)");
        rs = c.createStatement().executeQuery("select pg_size_pretty(pg_relation_size('ev_created_btree_tmp'))");
        rs.next();
        System.out.println("   (btree on created_at for comparison: " + rs.getString(1) + ")");

        System.out.println("== exclusion constraint: no overlapping bookings of one terminal");
        exec("""
                create table slot(terminal int, during tstzrange,
                    exclude using gist (terminal with =, during with &&));
                insert into slot values (7, '[2026-10-03 10:00, 2026-10-03 11:00)');
                """);
        for (String r : new String[] {"[2026-10-03 11:00, 2026-10-03 12:00)", "[2026-10-03 10:30, 2026-10-03 11:30)"}) {
            try {
                exec("insert into slot values (7, '" + r + "')");
                System.out.println("   " + r + " -> inserted");
            } catch (SQLException e) {
                System.out.println("   " + r + " -> " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse(""));
            }
        }
        c.close();
    }

    static void plan(String sql) throws SQLException {
        var rs = c.createStatement().executeQuery("explain (analyze, buffers, costs off, timing off) " + sql);
        String scan = null, buffers = null, time = null;
        while (rs.next()) {
            String l = rs.getString(1).strip();
            if (scan == null && l.contains("Scan")) scan = l.replace("->  ", "");
            if (buffers == null && l.startsWith("Buffers")) buffers = l;
            if (l.startsWith("Execution")) time = l;
        }
        System.out.println("   " + scan + "; " + buffers + "; " + time);
    }

    static void exec(String sql) throws SQLException { c.createStatement().execute(sql); }
}
