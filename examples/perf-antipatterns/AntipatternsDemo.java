import java.sql.*;
import java.util.*;

// Запуск: PostgreSQL 17 с задержкой сети 1 мс на localhost:55433 (см. README), затем
// java -cp "$(cat target/classpath.txt)" AntipatternsDemo.java
// 1 мс на круг — обычная цифра для базы в соседней зоне доступности.
public class AntipatternsDemo {
    static final String URL = "jdbc:postgresql://localhost:55433/postgres?user=postgres&password=pw";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            c.createStatement().execute("""
                drop table if exists item, orders;
                create table orders(id int primary key, customer int);
                create table item(order_id int references orders, sku text, price bigint);
                insert into orders select g, g % 1000 from generate_series(1, 20000) g;
                insert into item select o, 'sku-' || k, 100 + (o * k) % 900 from generate_series(1, 20000) o, generate_series(1, 5) k;
                create index on item(order_id);
                create index on orders(customer);
                analyze;
                """);
            System.out.println("== load 200 orders with their items (1 ms network round trip)");
            measure("N+1: one query per order", () -> nPlusOne(c));
            measure("one JOIN", () -> join(c));
            measure("two queries, = ANY(array)", () -> anyArray(c));

            System.out.println("== revenue of customer 7");
            measure("fetch all items, sum in Java", () -> sumInJava(c));
            measure("sum in SQL", () -> sumInSql(c));

            System.out.println("== insert 1000 rows");
            c.createStatement().execute("create temp table log(id int, msg text)");
            measure("one insert per row, autocommit", () -> insertEach(c));
            measure("JDBC batch, one transaction", () -> insertBatch(c));
        }
    }

    interface Work { String run() throws SQLException; }

    static void measure(String name, Work w) throws SQLException {
        w.run();                                            // прогрев
        long t = System.nanoTime();
        String result = w.run();
        System.out.printf("   %-32s %5d ms   %s%n", name, (System.nanoTime() - t) / 1_000_000, result);
    }

    static String nPlusOne(Connection c) throws SQLException {
        int queries = 1, items = 0;
        ResultSet o = c.createStatement().executeQuery("select id from orders order by id limit 200");
        var ps = c.prepareStatement("select sku, price from item where order_id = ?");
        while (o.next()) {
            ps.setInt(1, o.getInt(1));
            try (ResultSet r = ps.executeQuery()) { while (r.next()) items++; }
            queries++;
        }
        return queries + " queries, " + items + " items";
    }

    static String join(Connection c) throws SQLException {
        int items = 0;
        ResultSet r = c.createStatement().executeQuery("""
            select o.id, i.sku, i.price from (select id from orders order by id limit 200) o
            join item i on i.order_id = o.id""");
        while (r.next()) items++;
        return "1 query, " + items + " items";
    }

    static String anyArray(Connection c) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        ResultSet o = c.createStatement().executeQuery("select id from orders order by id limit 200");
        while (o.next()) ids.add(o.getInt(1));
        var ps = c.prepareStatement("select order_id, sku, price from item where order_id = any(?)");
        ps.setArray(1, c.createArrayOf("int4", ids.toArray()));
        int items = 0;
        try (ResultSet r = ps.executeQuery()) { while (r.next()) items++; }
        return "2 queries, " + items + " items";
    }

    static String sumInJava(Connection c) throws SQLException {
        long sum = 0; int rows = 0;
        ResultSet r = c.createStatement().executeQuery("select o.customer, i.price from item i join orders o on o.id = i.order_id");
        while (r.next()) { rows++; if (r.getInt(1) == 7) sum += r.getLong(2); }
        return "rows transferred " + rows + ", revenue " + sum;
    }

    static String sumInSql(Connection c) throws SQLException {
        ResultSet r = c.createStatement().executeQuery("select sum(i.price) from item i join orders o on o.id = i.order_id where o.customer = 7");
        r.next();
        return "rows transferred 1, revenue " + r.getLong(1);
    }

    static String insertEach(Connection c) throws SQLException {
        c.createStatement().execute("truncate log");
        var ps = c.prepareStatement("insert into log values (?, 'x')");
        for (int i = 0; i < 1000; i++) { ps.setInt(1, i); ps.executeUpdate(); }
        return "1000 round trips, 1000 commits";
    }

    static String insertBatch(Connection c) throws SQLException {
        c.createStatement().execute("truncate log");
        c.setAutoCommit(false);
        var ps = c.prepareStatement("insert into log values (?, 'x')");
        for (int i = 0; i < 1000; i++) { ps.setInt(1, i); ps.addBatch(); }
        ps.executeBatch();
        c.commit();
        c.setAutoCommit(true);
        return "batched round trips, 1 commit";
    }
}
