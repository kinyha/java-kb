import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: PostgreSQL 17 на localhost:55432 с расширением btree_gist (см. README), затем
// java -cp "$(cat target/classpath.txt)" BookingDemo.java
// 50 пользователей одновременно бронируют последнее место / пересекающиеся даты одного номера.
public class BookingDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    interface Attempt { boolean book(Connection c, int user) throws SQLException; }

    public static void main(String[] args) throws Exception {
        System.out.println("== 50 users try to book the last seat of flight SU-100");
        seats("check, then insert", BookingDemo::checkThenInsert);
        seats("conditional update", BookingDemo::conditionalUpdate);

        System.out.println("== 50 users book room 7 for overlapping dates");
        rooms("check overlap, then insert", false);
        rooms("exclusion constraint", true);

        System.out.println("== hold expires after 10 minutes");
        holds();
    }

    static void exec(Connection c, String sql) throws SQLException { c.createStatement().execute(sql); }

    // Наивно: посчитать брони, сравнить с вместимостью, вставить.
    static boolean checkThenInsert(Connection c, int user) throws SQLException {
        ResultSet r = c.createStatement().executeQuery(
            "select count(*) < (select capacity from flight where id = 'SU-100') from ticket where flight = 'SU-100'");
        r.next();
        if (!r.getBoolean(1)) return false;
        exec(c, "insert into ticket values ('SU-100', " + user + ")");
        return true;
    }

    // Счётчик свободных мест в строке рейса: уменьшается, только если больше нуля.
    static boolean conditionalUpdate(Connection c, int user) throws SQLException {
        int n = c.createStatement().executeUpdate("update flight set free = free - 1 where id = 'SU-100' and free > 0");
        if (n == 0) return false;
        exec(c, "insert into ticket values ('SU-100', " + user + ")");
        return true;
    }

    static void seats(String name, Attempt a) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                drop table if exists ticket, flight;
                create table flight(id text primary key, capacity int, free int check (free >= 0));
                insert into flight values ('SU-100', 180, 1);
                create table ticket(flight text, user_id int);
                insert into ticket select 'SU-100', -g from generate_series(1, 179) g;   -- 179 мест уже продано
                """);
        }
        int ok = race(a);
        System.out.printf("   %-28s confirmed %2d, tickets for 180 seats: %s%n", name, ok, scalar("select count(*) from ticket"));
    }

    static void rooms(String name, boolean constraint) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, "drop table if exists booking; create table booking(room int, stay daterange, user_id int)");
            if (constraint)   // никакие два бронирования одного номера не пересекаются по датам
                exec(c, "alter table booking add constraint no_overlap exclude using gist (room with =, stay with &&)");
        }
        int ok = race((c, user) -> {
            // Три ночи с заездом 10, 11 или 12 октября: любые два таких интервала пересекаются,
            // поэтому подтвердить можно ровно одно бронирование.
            String stay = "daterange('2026-10-10'::date + " + (user % 3) + ", '2026-10-13'::date + " + (user % 3) + ")";
            if (!constraint) {
                ResultSet r = c.createStatement().executeQuery("select count(*) from booking where room = 7 and stay && " + stay);
                r.next();
                if (r.getInt(1) > 0) return false;
            }
            try {
                exec(c, "insert into booking values (7, " + stay + ", " + user + ")");
                return true;
            } catch (SQLException e) {
                // 23P01 exclusion_violation; при одновременной проверке пересекающихся вставок
                // PostgreSQL иногда вместо него сообщает о взаимной блокировке 40P01. Оба значат «занято».
                if ("23P01".equals(e.getSQLState()) || "40P01".equals(e.getSQLState())) { c.rollback(); return false; }
                throw e;
            }
        });
        System.out.printf("   %-28s confirmed %2d, overlapping pairs: %s%n", name, ok, scalar("""
            select count(*) from booking a join booking b on a.room = b.room and a.ctid < b.ctid and a.stay && b.stay"""));
    }

    static void holds() throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                drop table if exists hold;
                create table hold(seat text primary key, user_id int, status text, expires_at timestamptz);
                insert into hold values
                  ('12A', 1, 'HELD', now() - interval '1 minute'),       -- бронь истекла, оплаты не было
                  ('12B', 2, 'HELD', now() + interval '9 minutes'),      -- ещё действует
                  ('12C', 3, 'CONFIRMED', now() - interval '1 hour');    -- оплачена, срок не важен
                """);
            // Новый пользователь пытается занять места: занять можно, если места нет или бронь истекла.
            for (String seat : List.of("12A", "12B", "12C")) {
                int n = c.createStatement().executeUpdate("""
                    insert into hold values ('%s', 9, 'HELD', now() + interval '10 minutes')
                    on conflict (seat) do update set user_id = 9, status = 'HELD', expires_at = excluded.expires_at
                    where hold.status = 'HELD' and hold.expires_at < now()""".formatted(seat));
                System.out.printf("   user 9 takes %s: %s%n", seat, n == 1 ? "held" : "busy");
            }
        }
    }

    static int race(Attempt a) throws Exception {
        var pool = Executors.newFixedThreadPool(50);
        var start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int u = 1; u <= 50; u++) {
            int user = u;
            fs.add(pool.submit(() -> {
                try (Connection c = DriverManager.getConnection(URL)) {
                    c.setAutoCommit(false);
                    start.await();
                    if (a.book(c, user)) ok.incrementAndGet();
                    c.commit();
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : fs) f.get();
        pool.shutdown();
        return ok.get();
    }

    static String scalar(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); ResultSet r = c.createStatement().executeQuery(sql)) {
            r.next(); return r.getString(1);
        }
    }
}
