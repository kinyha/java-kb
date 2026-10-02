import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" ModelingDemo.java
public class ModelingDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";
    static final SecureRandom rnd = new SecureRandom();

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            anomaly(c);
            constraints(c);
            keys(c);
        }
    }

    // ---------- аномалия обновления в денормализованной таблице ----------
    static void anomaly(Connection c) throws SQLException {
        exec(c, """
                drop table if exists payment_flat;
                create table payment_flat(id bigint primary key, merchant_id int, merchant_name text, amount bigint);
                insert into payment_flat values (1, 7, 'Coffee Point', 500), (2, 7, 'Coffee Point', 300), (3, 7, 'Coffee Point', 900);
                update payment_flat set merchant_name = 'Coffee Point Plus' where id = 3;   -- переименование «по месту»
                """);
        var rs = c.createStatement().executeQuery("select merchant_id, array_agg(distinct merchant_name) from payment_flat group by 1");
        rs.next();
        System.out.println("== denormalized: merchant " + rs.getInt(1) + " has names " + rs.getString(2));
    }

    // ---------- ограничения ловят плохие данные на входе ----------
    static void constraints(Connection c) throws SQLException {
        exec(c, """
                drop table if exists payment, merchant;
                create table merchant(id int primary key, inn text not null unique check (inn ~ '^[0-9]{10,12}$'));
                create table payment(
                    id bigint generated always as identity primary key,
                    merchant_id int not null references merchant(id),
                    order_id text not null,
                    amount_minor bigint not null check (amount_minor > 0),
                    currency char(3) not null check (currency in ('RUB', 'KZT', 'USD')),
                    unique (merchant_id, order_id));
                insert into merchant values (7, '7701234567');
                """);
        String[][] bad = {
                {"unknown merchant", "insert into payment(merchant_id, order_id, amount_minor, currency) values (99, 'o-1', 500, 'RUB')"},
                {"negative amount", "insert into payment(merchant_id, order_id, amount_minor, currency) values (7, 'o-1', -500, 'RUB')"},
                {"bad currency", "insert into payment(merchant_id, order_id, amount_minor, currency) values (7, 'o-1', 500, 'EUR')"},
                {"duplicate order", "insert into payment(merchant_id, order_id, amount_minor, currency) values (7, 'o-1', 500, 'RUB');"
                        + "insert into payment(merchant_id, order_id, amount_minor, currency) values (7, 'o-1', 700, 'RUB')"},
                {"bad INN", "insert into merchant values (8, '12-34')"}};
        for (String[] b : bad) {
            try {
                exec(c, b[1]);
                System.out.println("== " + b[0] + ": accepted");
            } catch (SQLException e) {
                System.out.printf("== %-17s %s %s%n", b[0] + ":", e.getSQLState(), e.getMessage().lines().findFirst().orElse("").replace("ERROR: ", ""));
            }
        }
    }

    // ---------- выбор ключа: bigint, UUIDv4, UUIDv7 ----------
    static void keys(Connection c) throws SQLException {
        exec(c, "drop table if exists k_big, k_v4, k_v7; create table k_big(id bigint primary key); "
                + "create table k_v4(id uuid primary key); create table k_v7(id uuid primary key)");
        c.setAutoCommit(false);
        int n = 2_000_000;
        for (String t : new String[] {"k_big", "k_v4", "k_v7"}) {
            var ps = c.prepareStatement("insert into " + t + " values (?)");
            long start = System.nanoTime();
            for (int i = 0; i < n; i++) {
                switch (t) {
                    case "k_big" -> ps.setLong(1, i);
                    case "k_v4" -> ps.setObject(1, UUID.randomUUID());
                    default -> ps.setObject(1, uuidV7());
                }
                ps.addBatch();
                if (i % 10_000 == 9_999) ps.executeBatch();
            }
            c.commit();
            long ms = (System.nanoTime() - start) / 1_000_000;
            var rs = c.createStatement().executeQuery("select pg_size_pretty(pg_relation_size('" + t + "_pkey'))");
            rs.next();
            System.out.printf("== %-6s %d inserts %5d ms, primary key index %s%n", t, n, ms, rs.getString(1));
            c.commit();
        }
        c.setAutoCommit(true);
    }

    // UUIDv7 (RFC 9562): 48 бит миллисекунд Unix-времени, 12-битный счётчик внутри миллисекунды
    // (метод 1 из RFC — монотонность в пределах процесса), затем случайные биты.
    static long lastMs, counter;
    static UUID uuidV7() {
        long ms = System.currentTimeMillis();
        if (ms == lastMs && counter < 0x0FFF) counter++;
        else { if (ms <= lastMs) ms = lastMs + 1; lastMs = ms; counter = 0; }
        long hi = (ms << 16) | 0x7000L | counter;
        long lo = (rnd.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(hi, lo);
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }
}
