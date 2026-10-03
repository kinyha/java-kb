import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" JobQueueDemo.java
// Очередь задач — таблица в PostgreSQL. Задача — отправить письмо, 20 мс.
public class JobQueueDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        System.out.println("== 400 jobs, 8 workers, each job takes 20 ms; row locked for the whole job");
        inTransaction("for update", "");
        inTransaction("for update skip locked", " skip locked");
        System.out.println("== same, but claim commits at once and the lease protects the job");
        workers("claim + lease", " skip locked");

        System.out.println("== worker crashes after taking a job; lease is 2 s");
        lease();

        System.out.println("== job fails: retry with backoff, then dead letter after 3 attempts");
        retries();
    }

    static void reset(Connection c, int jobs) throws SQLException {
        c.createStatement().execute("""
            drop table if exists job;
            create table job(
              id bigserial primary key,
              payload text,
              status text not null default 'READY',          -- READY, RUNNING, DONE, DEAD
              attempts int not null default 0,
              run_at timestamptz not null default now(),     -- не раньше этого времени (backoff)
              locked_until timestamptz,                      -- аренда: после истечения задачу берёт другой
              done_by text);
            create index on job (run_at) where status = 'READY';
            """);
        c.createStatement().execute("insert into job(payload) select 'mail-' || g from generate_series(1, " + jobs + ") g");
    }

    // Взять одну задачу: пометить RUNNING с арендой и закоммитить, потом работать вне транзакции.
    static Long claim(Connection c, String worker, String lockMode) throws SQLException {
        var p = c.prepareStatement("""
            update job set status = 'RUNNING', attempts = attempts + 1,
                           locked_until = now() + interval '2 seconds', done_by = ?
            where id = (
              select id from job
              where (status = 'READY' and run_at <= now())
                 or (status = 'RUNNING' and locked_until < now())     -- аренда истекла: воркер умер
              order by id limit 1
              for update""" + lockMode + """
            )
            returning id""");
        p.setString(1, worker);
        ResultSet r = p.executeQuery();
        return r.next() ? r.getLong(1) : null;
    }

    static void workers(String name, String lockMode) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) { reset(c, 400); }
        AtomicInteger done = new AtomicInteger(), empty = new AtomicInteger();
        long start = System.nanoTime();
        var pool = Executors.newFixedThreadPool(8);
        List<Future<?>> fs = new ArrayList<>();
        for (int w = 0; w < 8; w++) {
            String worker = "w" + w;
            fs.add(pool.submit(() -> {
                try (Connection c = DriverManager.getConnection(URL)) {
                    while (true) {
                        Long id = claim(c, worker, lockMode);
                        if (id == null) {
                            if (scalar("select count(*) from job where status <> 'DONE'") == 0) return null;
                            empty.incrementAndGet();             // взять нечего: все видимые строки заняты
                            Thread.sleep(5);
                            continue;
                        }
                        Thread.sleep(20);                        // работа
                        c.createStatement().executeUpdate("update job set status = 'DONE' where id = " + id);
                        done.incrementAndGet();
                    }
                }
            }));
        }
        for (var f : fs) f.get();
        pool.shutdown();
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("   %-24s done %d in %5d ms, empty polls %d%n", name, done.get(), ms, empty.get());
    }

    // Классический вариант: строка заблокирована, пока идёт работа, — транзакция длится 20 мс.
    static void inTransaction(String name, String lockMode) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) { reset(c, 400); }
        AtomicInteger done = new AtomicInteger(), empty = new AtomicInteger();
        long start = System.nanoTime();
        var pool = Executors.newFixedThreadPool(8);
        List<Future<?>> fs = new ArrayList<>();
        for (int w = 0; w < 8; w++) {
            fs.add(pool.submit(() -> {
                try (Connection c = DriverManager.getConnection(URL)) {
                    c.setAutoCommit(false);
                    while (true) {
                        ResultSet r = c.createStatement().executeQuery(
                            "select id from job where status = 'READY' order by id limit 1 for update" + lockMode);
                        if (!r.next()) {
                            c.commit();
                            if (scalar("select count(*) from job where status <> 'DONE'") == 0) return null;
                            empty.incrementAndGet();
                            continue;
                        }
                        long id = r.getLong(1);
                        Thread.sleep(20);                                           // работа под блокировкой
                        c.createStatement().executeUpdate("update job set status = 'DONE' where id = " + id);
                        c.commit();
                        done.incrementAndGet();
                    }
                }
            }));
        }
        for (var f : fs) f.get();
        pool.shutdown();
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("   %-24s done %d in %5d ms, empty polls %d%n", name, done.get(), ms, empty.get());
    }

    static void lease() throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            reset(c, 1);
            Long id = claim(c, "w-crashed", " skip locked");               // взял и «упал», не завершив
            System.out.println("   w-crashed took job " + id + ", status RUNNING");
            System.out.println("   w2 immediately: " + (claim(c, "w2", " skip locked") == null ? "nothing to take" : "took it"));
            Thread.sleep(2_200);
            Long again = claim(c, "w2", " skip locked");
            System.out.println("   w2 after 2.2 s: took job " + again + ", attempts " + scalar("select attempts from job where id = " + again));
        }
    }

    static void retries() throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            reset(c, 1);
            for (int i = 0; i < 4; i++) {
                Long id = null;
                while (id == null) { id = claim(c, "w1", " skip locked"); if (id == null) Thread.sleep(50); }
                int attempts = (int) scalar("select attempts from job where id = " + id);
                // Внешний сервис отвечает 503 каждый раз.
                if (attempts >= 3) {
                    c.createStatement().executeUpdate("update job set status = 'DEAD' where id = " + id);
                    System.out.println("   attempt " + attempts + ": failed -> DEAD, needs a human");
                    break;
                }
                long backoffMs = 200L << (attempts - 1);                    // 200, 400, 800 мс
                c.createStatement().executeUpdate("update job set status = 'READY', run_at = now() + interval '"
                        + backoffMs + " milliseconds' where id = " + id);
                System.out.println("   attempt " + attempts + ": failed -> retry in " + backoffMs + " ms");
            }
        }
    }

    static long scalar(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); ResultSet r = c.createStatement().executeQuery(sql)) {
            r.next(); return r.getLong(1);
        }
    }
}
