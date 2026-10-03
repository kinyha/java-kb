import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" EventStoreDemo.java
// Журнал событий заказов в одной таблице; состояние — свёртка событий; проекции — отдельные таблицы.
public class EventStoreDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    record Event(long position, String stream, int version, String type, long amount) {}

    // Состояние заказа восстанавливается из событий; само по себе нигде не хранится.
    record Order(String status, long paid, long refunded) {
        static final Order EMPTY = new Order("NONE", 0, 0);
        Order apply(Event e) {
            return switch (e.type()) {
                case "OrderPlaced" -> new Order("PLACED", paid, refunded);
                case "PaymentCaptured" -> new Order("PAID", paid + e.amount(), refunded);
                case "ItemRefunded" -> new Order(status, paid, refunded + e.amount());
                case "OrderShipped" -> new Order("SHIPPED", paid, refunded);
                default -> this;
            };
        }
    }

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            c.createStatement().execute("""
                drop table if exists event, order_view, refund_view, projector;
                create table event(
                  position bigserial primary key,           -- глобальный порядок для проекций
                  stream text not null, version int not null,
                  type text not null, amount bigint not null default 0,
                  unique (stream, version));                -- оптимистичная проверка версии
                create table order_view(id text primary key, status text, paid bigint);
                create table projector(name text primary key, position bigint);
                insert into projector values ('order_view', 0);
                """);
            append(c, "order-1", 0, "OrderPlaced", 0);
            append(c, "order-1", 1, "PaymentCaptured", 12000);
            append(c, "order-1", 2, "ItemRefunded", 3000);
            append(c, "order-2", 0, "OrderPlaced", 0);
            append(c, "order-2", 1, "PaymentCaptured", 5000);
        }

        System.out.println("== state is a fold of events");
        List<Event> events = read("order-1");
        Order o = Order.EMPTY;
        for (Event e : events) { o = o.apply(e); System.out.printf("   v%d %-16s -> %s%n", e.version(), e.type(), o); }

        System.out.println("== two commands on order-1 at version 3 at the same time");
        concurrent();

        System.out.println("== read model is updated asynchronously");
        try (Connection c = DriverManager.getConnection(URL)) {
            System.out.println("   view before projector: " + view(c, "order-1"));
            int n = project(c);
            System.out.println("   projector applied " + n + " events: " + view(c, "order-1"));
            append(c, "order-1", read("order-1").size(), "OrderShipped", 0);
            System.out.println("   after OrderShipped:    " + view(c, "order-1") + "  (stale until next run)");
            project(c);
            System.out.println("   next projector run:    " + view(c, "order-1"));

            System.out.println("== new read model built by replaying old events");
            c.createStatement().execute("create table refund_view as select stream, sum(amount) as refunded, count(*) as refunds "
                    + "from event where type = 'ItemRefunded' group by stream");
            ResultSet r = c.createStatement().executeQuery("select * from refund_view order by stream");
            while (r.next()) System.out.printf("   %s refunded %d in %d refund(s)%n", r.getString(1), r.getLong(2), r.getLong(3));
        }
    }

    // Добавить событие, ожидая, что текущая версия потока — expected. Иначе — конфликт.
    static void append(Connection c, String stream, int expected, String type, long amount) throws SQLException {
        var p = c.prepareStatement("insert into event(stream, version, type, amount) values (?, ?, ?, ?)");
        p.setString(1, stream); p.setInt(2, expected); p.setString(3, type); p.setLong(4, amount);
        p.executeUpdate();
    }

    static void concurrent() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        List<Future<String>> fs = new ArrayList<>();
        for (String[] cmd : new String[][] {{"refund 2000", "ItemRefunded", "2000"}, {"ship", "OrderShipped", "0"}}) {
            fs.add(pool.submit(() -> {
                try (Connection c = DriverManager.getConnection(URL)) {
                    int version = read("order-1").size();             // оба прочитали 3 события
                    start.await();
                    try {
                        append(c, "order-1", version, cmd[1], Long.parseLong(cmd[2]));
                        return cmd[0] + ": appended as v" + version;
                    } catch (SQLException e) {
                        return cmd[0] + ": conflict " + e.getSQLState() + ", reload and decide again";
                    }
                }
            }));
        }
        start.countDown();
        int appended = 0, conflicts = 0;
        for (var f : fs) if (f.get().contains("appended")) appended++; else conflicts++;
        // Какая из двух команд выиграла, зависит от планировщика; итог всегда один.
        System.out.println("   appended " + appended + ", conflicts " + conflicts + " (23505 on unique (stream, version))");
        pool.shutdown();
        // Проигравший перечитывает поток и решает заново — в демо просто откатываем: ничего не добавляем.
        try (Connection c = DriverManager.getConnection(URL)) {
            c.createStatement().execute("delete from event where stream = 'order-1' and version = 3");
        }
    }

    // Проекция читает события после своей позиции и обновляет таблицу представления в той же транзакции.
    static int project(Connection c) throws SQLException {
        c.setAutoCommit(false);
        ResultSet r = c.createStatement().executeQuery("select position from projector where name = 'order_view' for update");
        r.next();
        long from = r.getLong(1), last = from;
        var ps = c.prepareStatement("select position, stream, version, type, amount from event where position > ? order by position");
        ps.setLong(1, from);
        r = ps.executeQuery();
        int n = 0;
        while (r.next()) {
            Event e = new Event(r.getLong(1), r.getString(2), r.getInt(3), r.getString(4), r.getLong(5));
            var up = c.prepareStatement("""
                insert into order_view values (?, ?, ?) on conflict (id) do update
                set status = case when ? = 'ItemRefunded' then order_view.status else excluded.status end,
                    paid = order_view.paid + excluded.paid""");
            String status = switch (e.type()) { case "OrderPlaced" -> "PLACED"; case "PaymentCaptured" -> "PAID"; case "OrderShipped" -> "SHIPPED"; default -> null; };
            up.setString(1, e.stream()); up.setString(2, status); up.setLong(3, e.type().equals("PaymentCaptured") ? e.amount() : 0); up.setString(4, e.type());
            up.executeUpdate();
            last = e.position(); n++;
        }
        var save = c.prepareStatement("update projector set position = ? where name = 'order_view'");
        save.setLong(1, last); save.executeUpdate();
        c.commit();
        c.setAutoCommit(true);
        return n;
    }

    static String view(Connection c, String id) throws SQLException {
        var p = c.prepareStatement("select status, paid from order_view where id = ?");
        p.setString(1, id);
        ResultSet r = p.executeQuery();
        return r.next() ? r.getString(1) + ", paid " + r.getLong(2) : "missing";
    }

    static List<Event> read(String stream) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) {
            var p = c.prepareStatement("select position, stream, version, type, amount from event where stream = ? order by version");
            p.setString(1, stream);
            ResultSet r = p.executeQuery();
            List<Event> out = new ArrayList<>();
            while (r.next()) out.add(new Event(r.getLong(1), r.getString(2), r.getInt(3), r.getString(4), r.getLong(5)));
            return out;
        }
    }
}
