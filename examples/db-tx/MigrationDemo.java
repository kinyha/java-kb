import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" MigrationDemo.java
// Пока идёт DDL, второе соединение вставляет платежи; печатаем самое долгое ожидание вставки.
public class MigrationDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                    drop table if exists payment;
                    create table payment(id bigserial primary key, merchant int, amount bigint, note text);
                    insert into payment(merchant, amount) select g % 1000, g % 5000 from generate_series(1, 3000000) g;
                    """);
            exec(c, "vacuum analyze payment");
            ddl(c, "add column, constant default", "alter table payment add column currency text default 'RUB'");
            ddl(c, "add column, volatile default", "alter table payment add column created_at timestamptz default clock_timestamp()");
            ddl(c, "create index", "create index payment_merchant_idx on payment(merchant)");
            exec(c, "drop index payment_merchant_idx");
            ddl(c, "create index concurrently", "create index concurrently payment_merchant_idx on payment(merchant)");
            ddl(c, "set not null (full scan)", "alter table payment alter column amount set not null");
            exec(c, "alter table payment alter column amount drop not null");
            ddl(c, "add check not valid", "alter table payment add constraint amount_nn check (amount is not null) not valid");
            ddl(c, "validate constraint", "alter table payment validate constraint amount_nn");
            ddl(c, "set not null (uses valid check)", "alter table payment alter column amount set not null");
        }
    }

    static void ddl(Connection c, String title, String sql) throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong worst = new AtomicLong();
        Thread writer = Thread.ofPlatform().start(() -> {
            try (Connection w = DriverManager.getConnection(URL)) {
                var ps = w.prepareStatement("insert into payment(merchant, amount) values (1, 100)");
                while (!stop.get()) {
                    long t = System.nanoTime();
                    ps.executeUpdate();
                    worst.accumulateAndGet((System.nanoTime() - t) / 1_000_000, Math::max);
                    Thread.sleep(5);
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        Thread.sleep(200);
        long t = System.nanoTime();
        exec(c, sql);
        long ddlMs = (System.nanoTime() - t) / 1_000_000;
        Thread.sleep(200);
        stop.set(true);
        writer.join();
        System.out.printf("== %-32s DDL %5d ms, worst concurrent insert %5d ms%n", title + ":", ddlMs, worst.get());
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }
}
