import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" ArchitectureDemo.java
public class ArchitectureDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            processes(c);
            buffers(c);
            wal(c);
            commits(c);
            fullPageWrites(c);
            toast(c);
        }
    }

    // ---------- процесс на соединение ----------
    static void processes(Connection c) throws Exception {
        System.out.println("== background processes");
        var rs = c.createStatement().executeQuery("select backend_type, count(*) from pg_stat_activity group by 1 order by 1");
        while (rs.next()) System.out.printf("   %-30s %d%n", rs.getString(1), rs.getLong(2));
        List<Connection> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) pool.add(DriverManager.getConnection(URL));
        rs = c.createStatement().executeQuery("select count(*) from pg_stat_activity where backend_type = 'client backend'");
        rs.next();
        System.out.println("== after opening 20 more connections: client backends = " + rs.getLong(1) + " (one OS process each)");
        for (Connection p : pool) p.close();
    }

    // ---------- shared buffers: чтение с диска и из кеша ----------
    static void buffers(Connection c) throws Exception {
        exec(c, """
                drop table if exists payment;
                create table payment(id bigint primary key, merchant int, amount bigint, details text);
                insert into payment select g, g % 100, g % 10000, null from generate_series(1, 300000) g;
                """);
        new ProcessBuilder("docker", "restart", "kb-pg").start().waitFor();   // Пустой shared buffers.
        Thread.sleep(4000);
        try (Connection fresh = DriverManager.getConnection(URL)) {
            for (String run : new String[] {"cold", "warm"}) {
                exec(fresh, "set max_parallel_workers_per_gather = 0");      // Один процесс — одна строка Buffers.
                var rs = fresh.createStatement().executeQuery("explain (analyze, buffers, costs off) select sum(amount) from payment");
                boolean first = true;
                while (rs.next()) {
                    String l = rs.getString(1);
                    if ((l.contains("Buffers") && first) || l.contains("Execution Time")) {
                        System.out.println("== " + run + ": " + l.trim());
                        if (l.contains("Buffers")) first = false;
                    }
                }
            }
            var rs = fresh.createStatement().executeQuery("show shared_buffers");
            rs.next();
            System.out.println("   shared_buffers = " + rs.getString(1));
        }
    }

    // ---------- WAL: сколько журнала даёт вставка ----------
    static void wal(Connection c) throws Exception {
        try (Connection fresh = DriverManager.getConnection(URL)) {
            exec(fresh, "checkpoint");
            String before = scalar(fresh, "select pg_current_wal_lsn()");
            exec(fresh, "insert into payment select g, 1, 500, null from generate_series(300001, 310000) g");
            String after = scalar(fresh, "select pg_current_wal_lsn()");
            System.out.println("== WAL for 10k inserted rows: " + scalar(fresh,
                    "select pg_size_pretty(pg_wal_lsn_diff('" + after + "', '" + before + "'))") + " (lsn " + before + " -> " + after + ")");
        }
    }

    // ---------- цена ожидания fsync на коммите ----------
    static void commits(Connection c) throws Exception {
        try (Connection fresh = DriverManager.getConnection(URL)) {
            fresh.setAutoCommit(true);
            for (String mode : new String[] {"on", "off"}) {
                exec(fresh, "set synchronous_commit = " + mode);
                long t = System.nanoTime();
                for (int i = 0; i < 1000; i++) exec(fresh, "update payment set amount = amount + 1 where id = " + (i + 1));
                System.out.printf("== 1000 single-row commits, synchronous_commit=%-3s %.2f ms per commit%n", mode, (System.nanoTime() - t) / 1e6 / 1000);
            }
        }
    }

    // ---------- full page writes после checkpoint ----------
    static void fullPageWrites(Connection c) throws Exception {
        try (Connection fresh = DriverManager.getConnection(URL)) {
            exec(fresh, "checkpoint");
            for (String run : new String[] {"first UPDATE after checkpoint", "same UPDATE again"}) {
                String before = scalar(fresh, "select pg_current_wal_lsn()");
                exec(fresh, "update payment set amount = amount + 1 where id % 100 = 0");   // 3100 строк по всей таблице.
                String after = scalar(fresh, "select pg_current_wal_lsn()");
                System.out.printf("== %-30s WAL %s%n", run + ":", scalar(fresh,
                        "select pg_size_pretty(pg_wal_lsn_diff('" + after + "', '" + before + "'))"));
            }
        }
    }

    // ---------- TOAST: большие значения живут отдельно ----------
    static void toast(Connection c) throws Exception {
        try (Connection fresh = DriverManager.getConnection(URL)) {
            exec(fresh, """
                    update payment set details = (select string_agg(md5(random()::text), '') from generate_series(1, 200 + id % 1))
                    where id <= 20000""");                                  // ~6 КБ несжимаемых данных; id % 1 делает подзапрос зависимым от строки.
            exec(fresh, "vacuum payment");
            System.out.println("== TOAST: main " + scalar(fresh, "select pg_size_pretty(pg_relation_size('payment'))")
                    + ", toast " + scalar(fresh, "select pg_size_pretty(pg_relation_size(reltoastrelid)) from pg_class where relname = 'payment'"));
            for (String q : new String[] {"select sum(amount) from payment where id <= 20000",
                    "select sum(length(details)) from payment where id <= 20000"}) {
                var rs = fresh.createStatement().executeQuery("explain (analyze, buffers, costs off) " + q);
                while (rs.next()) {
                    String l = rs.getString(1);
                    if (l.contains("Buffers")) { System.out.println("   " + q.substring(7, q.indexOf(" from")) + ": " + l.trim()); break; }
                }
            }
        }
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }

    static String scalar(Connection c, String sql) throws SQLException {
        var rs = c.createStatement().executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }
}
