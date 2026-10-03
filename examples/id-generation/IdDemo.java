import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.function.Supplier;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" IdDemo.java
public class IdDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw&reWriteBatchedInserts=true";
    static final int ROWS = 1_000_000;
    static final SecureRandom rnd = new SecureRandom();

    // UUID v7 (RFC 9562): 48 бит миллисекунд Unix, версия 7, 74 случайных бита.
    // Монотонности внутри одной миллисекунды здесь нет — порядок только по времени.
    static UUID uuidV7() {
        long ms = System.currentTimeMillis();
        long hi = (ms << 16) | 0x7000L | (rnd.nextInt() & 0x0fff);
        long lo = (rnd.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;   // вариант 10
        return new UUID(hi, lo);
    }

    // Монотонный v7 (RFC 9562, метод 1): в 12 битах rand_a — счётчик внутри миллисекунды,
    // поэтому идентификаторы одного процесса строго возрастают.
    static long lastMs = -1, counter;
    static synchronized UUID uuidV7Monotonic() {
        long ms = System.currentTimeMillis();
        if (ms <= lastMs) { ms = lastMs; if (++counter > 0xfff) { ms++; counter = 0; } }   // переполнение — «занимаем» следующую мс
        else counter = 0;
        lastMs = ms;
        long hi = (ms << 16) | 0x7000L | counter;
        long lo = (rnd.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(hi, lo);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== sample ids");
        System.out.println("   v4: " + UUID.randomUUID() + "  " + UUID.randomUUID());
        UUID a = uuidV7(); Thread.sleep(2); UUID b = uuidV7();
        System.out.println("   v7: " + a + "  " + b + "  version " + b.version());

        System.out.println("== insert 1,000,000 rows into a table with a primary key, PostgreSQL 17");
        try (Connection c = DriverManager.getConnection(URL)) {
            run(c, "bigserial", "id bigserial primary key", null);
            run(c, "uuid v4", "id uuid primary key", UUID::randomUUID);
            run(c, "uuid v7", "id uuid primary key", IdDemo::uuidV7);
            run(c, "v7 mono", "id uuid primary key", IdDemo::uuidV7Monotonic);
        }

        System.out.println("== snowflake: 41 bits ms, 10 bits node, 12 bits sequence");
        var sf = new Snowflake(7);
        long first = sf.next(), second = sf.next();
        System.out.println("   two ids: " + first + " " + second + " increasing " + (second > first));
        System.out.println("   decoded: node " + ((first >> 12) & 0x3ff) + ", seq " + (first & 0xfff) + "/" + (second & 0xfff));
        sf.clockBackwards = 5;                                  // NTP отвёл часы назад на 5 мс
        try { sf.next(); } catch (IllegalStateException e) { System.out.println("   clock moved back: " + e.getMessage().replaceAll("\\d+ ms", "N ms")); }
    }

    static void run(Connection c, String name, String column, Supplier<UUID> gen) throws Exception {
        c.createStatement().execute("drop table if exists t; create table t(" + column + ", payload text)");
        c.setAutoCommit(false);
        long start = System.nanoTime();
        var ps = gen == null ? c.prepareStatement("insert into t(payload) values ('x')")
                             : c.prepareStatement("insert into t(id, payload) values (?, 'x')");
        for (int i = 1; i <= ROWS; i++) {
            if (gen != null) ps.setObject(1, gen.get());
            ps.addBatch();
            if (i % 10_000 == 0) { ps.executeBatch(); c.commit(); }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        c.setAutoCommit(true);
        ResultSet r = c.createStatement().executeQuery("""
            select pg_size_pretty(pg_relation_size('t_pkey')),
                   (select (100 - avg_leaf_density)::int from pgstatindex('t_pkey'))""");
        r.next();
        System.out.printf("   %-10s %5d ms   pk index %-7s  free space in leaf pages %d%%%n", name, ms, r.getString(1), r.getInt(2));
    }

    // Snowflake: время, номер узла и счётчик в одном long. Уникален без координации,
    // пока у узлов разные номера и часы не идут назад.
    static class Snowflake {
        static final long EPOCH = 1_735_689_600_000L;          // 2025-01-01T00:00:00Z
        final long node;
        long lastMs = -1, seq;
        long clockBackwards;
        Snowflake(long node) { this.node = node; }

        synchronized long next() {
            long ms = System.currentTimeMillis() - clockBackwards;
            if (ms < lastMs) throw new IllegalStateException("refusing ids for " + (lastMs - ms) + " ms");
            if (ms == lastMs) {
                seq = (seq + 1) & 0xfff;
                if (seq == 0) while ((ms = System.currentTimeMillis()) <= lastMs) { }  // 4096 за мс исчерпаны
            } else seq = 0;
            lastMs = ms;
            return ((ms - EPOCH) << 22) | (node << 12) | seq;
        }
    }
}
