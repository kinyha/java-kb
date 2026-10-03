import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.util.Random;
import redis.clients.jedis.RedisClient;

// Запуск: Redis на 6379 и PostgreSQL на 55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" LatencyDemo.java
// Порядки задержек на этой машине. Один поток, прогрев перед замером.
public class LatencyDemo {
    public static void main(String[] args) throws Exception {
        System.out.println("== memory, 512 MB long[]");
        memory();
        System.out.println("== disk (SSD, APFS)");
        disk();
        System.out.println("== network round trips");
        loopback();
        redis();
        postgres();
    }

    static void memory() {
        int n = 64 * 1024 * 1024;                       // 64M long = 512 MB
        long[] a = new long[n];
        // Случайная перестановка-цикл: каждый следующий индекс зависит от предыдущего (pointer chasing).
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        Random r = new Random(1);
        for (int i = n - 1; i > 0; i--) { int j = r.nextInt(i + 1); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
        for (int i = 0; i < n; i++) a[perm[i]] = perm[(i + 1) % n];
        perm = null;
        long sum = 0;
        for (int rep = 0; rep < 3; rep++) {                  // последний повтор — замер
            long t = System.nanoTime();
            for (int i = 0; i < n; i++) sum += a[i];
            long seq = System.nanoTime() - t;
            t = System.nanoTime();
            int p = 0;
            for (int i = 0; i < 10_000_000; i++) p = (int) a[p];
            long rnd = System.nanoTime() - t;
            sum += p;
            if (rep == 2) {
                System.out.printf("   sequential read: %.1f GB/s (%.2f ns per 8-byte value)%n", n * 8.0 / seq, (double) seq / n);
                System.out.printf("   random read:     %.0f ns per dependent access%n", rnd / 10_000_000.0);
            }
        }
        if (sum == 42) System.out.println();
    }

    static void disk() throws IOException {
        Path f = Files.createTempFile("lat", ".bin");
        try (FileChannel ch = FileChannel.open(f, StandardOpenOption.WRITE)) {
            ByteBuffer big = ByteBuffer.allocateDirect(64 * 1024 * 1024);
            long t = System.nanoTime();
            for (int i = 0; i < 16; i++) { big.clear(); ch.write(big); }     // 1 GB
            ch.force(true);
            long ns = System.nanoTime() - t;
            System.out.printf("   write 1 GB + fsync: %.1f GB/s%n", 1024.0 * 1024 * 1024 / ns);
            ByteBuffer small = ByteBuffer.allocateDirect(4096);
            for (int warm = 0; warm < 2; warm++) {
                t = System.nanoTime();
                for (int i = 0; i < 200; i++) { small.clear(); ch.write(small, (long) i * 4096); ch.force(false); }
                ns = System.nanoTime() - t;
            }
            System.out.printf("   4 KB write + fsync: %.0f us each%n", ns / 200 / 1000.0);
        } finally { Files.delete(f); }
    }

    static void loopback() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            Thread echo = Thread.ofPlatform().daemon().start(() -> {
                try (Socket s = ss.accept(); var in = s.getInputStream(); var out = s.getOutputStream()) {
                    s.setTcpNoDelay(true);
                    int b; while ((b = in.read()) >= 0) out.write(b);
                } catch (IOException ignored) { }
            });
            try (Socket c = new Socket("localhost", ss.getLocalPort())) {
                c.setTcpNoDelay(true);
                var in = c.getInputStream(); var out = c.getOutputStream();
                long ns = 0;
                for (int rep = 0; rep < 2; rep++) {
                    long t = System.nanoTime();
                    for (int i = 0; i < 20_000; i++) { out.write(1); in.read(); }
                    ns = System.nanoTime() - t;
                }
                System.out.printf("   TCP loopback ping:     %.0f us%n", ns / 20_000 / 1000.0);
            }
        }
    }

    static void redis() {
        try (RedisClient r = RedisClient.create("redis://localhost:6379")) {
            r.set("lat", "x");
            long ns = 0;
            for (int rep = 0; rep < 2; rep++) {
                long t = System.nanoTime();
                for (int i = 0; i < 20_000; i++) r.get("lat");
                ns = System.nanoTime() - t;
            }
            System.out.printf("   Redis GET (Docker):    %.0f us%n", ns / 20_000 / 1000.0);
        }
    }

    static void postgres() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw");
             PreparedStatement ps = c.prepareStatement("select 1")) {
            long ns = 0;
            for (int rep = 0; rep < 2; rep++) {
                long t = System.nanoTime();
                for (int i = 0; i < 20_000; i++) try (ResultSet rs = ps.executeQuery()) { rs.next(); }
                ns = System.nanoTime() - t;
            }
            System.out.printf("   PostgreSQL select 1:   %.0f us%n", ns / 20_000 / 1000.0);
            c.setAutoCommit(false);
            c.createStatement().execute("create temp table if not exists w(x int)");
            c.commit();
            ps.close();
            var ins = c.prepareStatement("insert into w values (1)");
            for (int rep = 0; rep < 2; rep++) {
                long t = System.nanoTime();
                for (int i = 0; i < 2_000; i++) { ins.executeUpdate(); c.commit(); }
                ns = System.nanoTime() - t;
            }
            System.out.printf("   PostgreSQL insert+commit: %.0f us%n", ns / 2_000 / 1000.0);
        }
    }
}
