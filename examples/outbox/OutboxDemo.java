import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Запуск: PostgreSQL 17 с wal_level=logical на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" OutboxDemo.java
public class OutboxDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    // Брокер-заглушка: журнал опубликованного и рубильник «недоступен».
    static final List<String> broker = Collections.synchronizedList(new ArrayList<>());
    static volatile boolean brokerDown;

    static void publish(String event) {
        if (brokerDown) throw new IllegalStateException("broker unavailable");
        broker.add(event);
    }

    public static void main(String[] args) throws Exception {
        try (Connection db = DriverManager.getConnection(URL)) {
            schema(db);
            dualWriteLost(db);
            dualWritePhantom(db);
            outbox(db);
            competingRelays();
            duplicateAfterCrash(db);
            cdc(db);
        }
    }

    static void schema(Connection db) throws SQLException {
        try (Statement s = db.createStatement()) {
            s.execute("select pg_drop_replication_slot(slot_name) from pg_replication_slots where slot_name = 'outbox_slot'");
            s.execute("drop table if exists payment, outbox, processed_event");
            s.execute("create table payment(id text primary key, amount bigint not null check (amount > 0))");
            s.execute("""
                    create table outbox(id bigserial primary key, aggregate_id text not null,
                                        type text not null, payload text not null, created_at timestamptz default now())""");
            s.execute("create table processed_event(event_id bigint primary key)");
        }
    }

    // ---------- двойная запись: сначала база, потом брокер ----------
    static void dualWriteLost(Connection db) throws SQLException {
        brokerDown = true;
        db.setAutoCommit(false);
        insertPayment(db, "p-1", 500);
        db.commit();                                               // Платёж сохранён.
        try {
            publish("PaymentCreated p-1");                         // Брокер недоступен или процесс упал.
        } catch (IllegalStateException e) {
            System.out.println("== commit, then publish: " + e.getMessage());
        }
        brokerDown = false;
        System.out.println("payment p-1 in db: " + count(db, "select count(*) from payment where id = 'p-1'")
                + ", PaymentCreated p-1 in broker: " + broker.contains("PaymentCreated p-1"));
    }

    // ---------- двойная запись: сначала брокер, потом коммит ----------
    static void dualWritePhantom(Connection db) throws SQLException {
        db.setAutoCommit(false);
        insertPayment(db, "p-2", 700);
        publish("PaymentCreated p-2");                             // Ушло до коммита.
        try {
            insertPayment(db, "p-2b", -5);                         // Следующий шаг транзакции нарушает check.
            db.commit();
        } catch (SQLException e) {
            db.rollback();
            System.out.println("== publish, then commit: " + e.getSQLState() + " rollback");
        }
        System.out.println("payment p-2 in db: " + count(db, "select count(*) from payment where id = 'p-2'")
                + ", PaymentCreated p-2 in broker: " + broker.contains("PaymentCreated p-2"));
    }

    // ---------- outbox: событие пишется в той же транзакции ----------
    static void outbox(Connection db) throws SQLException {
        brokerDown = true;
        db.setAutoCommit(false);
        insertPayment(db, "p-3", 900);
        insertOutbox(db, "p-3", "PaymentCreated");                 // Та же транзакция.
        db.commit();
        int sent = relayOnce(db, 10);                              // Брокер лежит: ничего не отправлено.
        System.out.println("== outbox, broker down: relay sent " + sent + ", pending " + count(db, "select count(*) from outbox"));
        brokerDown = false;
        sent = relayOnce(db, 10);
        System.out.println("== outbox, broker up:   relay sent " + sent + ", pending " + count(db, "select count(*) from outbox")
                + ", broker has " + broker.stream().filter(e -> e.contains("p-3 ")).toList());
    }

    // Один проход ретранслятора: забрать пачку, опубликовать, удалить — в одной транзакции.
    static int relayOnce(Connection db, int batch) throws SQLException {
        db.setAutoCommit(false);
        var ids = new ArrayList<Long>();
        try (var ps = db.prepareStatement(
                "select id, type, aggregate_id from outbox order by id limit ? for update skip locked")) {
            ps.setInt(1, batch);
            var rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    publish(rs.getString(2) + " " + rs.getString(3) + " #" + rs.getLong(1));
                    ids.add(rs.getLong(1));
                }
            } catch (IllegalStateException e) {
                db.rollback();                                    // Строки остаются, попробуем позже.
                return 0;
            }
        }
        try (var del = db.prepareStatement("delete from outbox where id = any(?)")) {
            del.setArray(1, db.createArrayOf("bigint", ids.toArray()));
            del.executeUpdate();
        }
        db.commit();
        return ids.size();
    }

    // ---------- два ретранслятора не берут одни и те же строки ----------
    static void competingRelays() throws Exception {
        try (Connection db = DriverManager.getConnection(URL)) {
            db.setAutoCommit(false);
            for (int i = 1; i <= 6; i++) insertOutbox(db, "p-" + (10 + i), "PaymentCreated");
            db.commit();
        }
        broker.clear();
        // Ретранслятор держит блокировку пачки, пока «публикует».
        var a = CompletableFuture.supplyAsync(() -> slowRelay("A"));
        Thread.sleep(100);
        var b = CompletableFuture.supplyAsync(() -> slowRelay("B"));
        System.out.println("== two relays, skip locked: A took " + a.join() + ", B took " + b.join());
    }

    static List<Long> slowRelay(String name) {
        try (Connection db = DriverManager.getConnection(URL)) {
            db.setAutoCommit(false);
            var ids = new ArrayList<Long>();
            var rs = db.createStatement().executeQuery("select id from outbox order by id limit 3 for update skip locked");
            while (rs.next()) ids.add(rs.getLong(1));
            Thread.sleep(300);
            db.createStatement().executeUpdate("delete from outbox where id in (" + join(ids) + ")");
            db.commit();
            return ids;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---------- падение между публикацией и удалением даёт дубль ----------
    static void duplicateAfterCrash(Connection db) throws SQLException {
        broker.clear();
        db.setAutoCommit(false);
        insertPayment(db, "p-4", 300);
        long eventId = insertOutbox(db, "p-4", "PaymentCreated");
        db.commit();
        // Первый ретранслятор опубликовал, но упал до commit удаления.
        publish("PaymentCreated p-4 #" + eventId);
        db.rollback();
        relayOnce(db, 10);                                         // Следующий проход публикует снова.
        System.out.println("== crash before delete: broker has " + broker);

        db.setAutoCommit(true);
        for (String e : broker) {
            long id = Long.parseLong(e.substring(e.indexOf('#') + 1));
            try (var ps = db.prepareStatement("insert into processed_event values (?) on conflict do nothing")) {
                ps.setLong(1, id);
                System.out.println("consumer " + e + " -> " + (ps.executeUpdate() == 1 ? "processed" : "duplicate, skipped"));
            }
        }
    }

    // ---------- CDC: событие видно в WAL после коммита ----------
    static void cdc(Connection db) throws SQLException {
        db.setAutoCommit(true);
        try (Statement s = db.createStatement()) {
            s.execute("select pg_create_logical_replication_slot('outbox_slot', 'test_decoding')");
        }
        db.setAutoCommit(false);
        insertPayment(db, "p-5", 1200);
        insertOutbox(db, "p-5", "PaymentCreated");
        db.commit();
        db.setAutoCommit(true);
        System.out.println("== CDC: logical decoding of WAL");
        var rs = db.createStatement().executeQuery("select data from pg_logical_slot_get_changes('outbox_slot', null, null)");
        while (rs.next()) System.out.println(rs.getString(1));
        db.createStatement().execute("select pg_drop_replication_slot('outbox_slot')");
    }

    // ---------- утилиты ----------
    static void insertPayment(Connection db, String id, long amount) throws SQLException {
        try (var ps = db.prepareStatement("insert into payment values (?, ?)")) {
            ps.setString(1, id);
            ps.setLong(2, amount);
            ps.executeUpdate();
        }
    }

    static long insertOutbox(Connection db, String aggregate, String type) throws SQLException {
        try (var ps = db.prepareStatement("insert into outbox(aggregate_id, type, payload) values (?, ?, ?) returning id")) {
            ps.setString(1, aggregate);
            ps.setString(2, type);
            ps.setString(3, "{\"paymentId\":\"" + aggregate + "\"}");
            var rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    static long count(Connection db, String sql) throws SQLException {
        var rs = db.createStatement().executeQuery(sql);
        rs.next();
        return rs.getLong(1);
    }

    static String join(List<Long> ids) {
        return ids.isEmpty() ? "null" : String.join(",", ids.stream().map(String::valueOf).toList());
    }
}
