import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Запуск: PostgreSQL 17 на localhost:55432 (см. README), затем
// java -cp "$(cat target/classpath.txt)" PaymentDemo.java
// База — настоящая; PSP (платёжный провайдер) — модель в памяти с его типичными свойствами:
// идемпотентность по ключу, потерянные ответы, вебхуки, файл расчётов в конце дня.
public class PaymentDemo {
    static final String URL = "jdbc:postgresql://localhost:55432/postgres?user=postgres&password=pw";

    static final Psp PSP = new Psp();

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                drop table if exists ledger, payment;
                create table payment(
                  id bigserial primary key,
                  merchant text not null,
                  idempotency_key text not null,
                  amount bigint not null check (amount > 0),        -- в копейках, без double
                  currency char(3) not null,
                  status text not null default 'CREATED',
                  psp_ref text,
                  created_at timestamptz not null default now(),
                  unique (merchant, idempotency_key));               -- повтор того же запроса — та же строка
                create table ledger(
                  payment_id bigint references payment, account text, amount bigint);
                """);
        }
        concurrentRetries();
        lostResponse();
        webhooks();
        reconciliation();
    }

    // ---------- 1. Клиент повторил запрос, пока первый ещё выполнялся ----------
    static void concurrentRetries() throws Exception {
        System.out.println("== 1. three concurrent requests with one Idempotency-Key");
        var pool = Executors.newFixedThreadPool(3);
        var start = new CountDownLatch(1);
        List<Future<Long>> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++)
            ids.add(pool.submit(() -> { start.await(); return createPayment("shop-7", "order-42", 150_00); }));
        start.countDown();
        var answered = new java.util.TreeSet<Long>();
        for (var f : ids) answered.add(f.get());
        System.out.println("   distinct payment ids in 3 responses: " + answered.size());
        pool.shutdown();
        System.out.println("   rows in payment: " + scalar("select count(*) from payment"));
    }

    // INSERT ... ON CONFLICT DO NOTHING: уникальный индекс решает гонку, а не проверка «есть ли уже».
    static long createPayment(String merchant, String key, long amount) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) {
            var ins = c.prepareStatement("""
                insert into payment(merchant, idempotency_key, amount, currency) values (?, ?, ?, 'EUR')
                on conflict (merchant, idempotency_key) do nothing returning id""");
            ins.setString(1, merchant); ins.setString(2, key); ins.setLong(3, amount);
            ResultSet r = ins.executeQuery();
            if (r.next()) return r.getLong(1);
            var sel = c.prepareStatement("select id, amount from payment where merchant = ? and idempotency_key = ?");
            sel.setString(1, merchant); sel.setString(2, key);
            r = sel.executeQuery(); r.next();
            if (r.getLong(2) != amount) throw new IllegalStateException("same key, different amount"); // 422 клиенту
            return r.getLong(1);
        }
    }

    // ---------- 2. PSP списал деньги, но ответ потерялся ----------
    static void lostResponse() throws Exception {
        System.out.println("== 2. PSP charged the card, the response was lost");
        for (boolean samePspKey : new boolean[] {false, true}) {
            var psp = PSP;
            int before = psp.charges.size();
            long id = createPayment("shop-7", "order-" + (samePspKey ? 44 : 43), 80_00);
            psp.loseNextResponse = true;
            try {
                charge(psp, id, samePspKey ? "pay-" + id : "pay-" + id + "-try1");
            } catch (SocketTimeoutException e) {
                System.out.println("   attempt 1: " + e.getMessage() + ", status " + status(id));
            }
            charge(psp, id, samePspKey ? "pay-" + id : "pay-" + id + "-try2");   // повтор после таймаута
            System.out.printf("   %-26s PSP charges: %d, status %s%n",
                    samePspKey ? "retry with same PSP key:" : "retry with a new PSP key:", psp.charges.size() - before, status(id));
        }
    }

    // Статус PENDING записываем ДО вызова PSP: после сбоя видно, что исход неизвестен.
    static void charge(Psp psp, long id, String pspKey) throws Exception {
        update("update payment set status = 'PENDING' where id = ? and status in ('CREATED', 'PENDING')", id);
        String ref = psp.charge(pspKey, id, scalar("select amount from payment where id = " + id));
        try (Connection c = DriverManager.getConnection(URL)) {
            c.setAutoCommit(false);
            var u = c.prepareStatement("update payment set status = 'SUCCEEDED', psp_ref = ? where id = ? and status = 'PENDING'");
            u.setString(1, ref); u.setLong(2, id);
            if (u.executeUpdate() == 1) post(c, id);       // проводки — в той же транзакции, что и смена статуса
            c.commit();
        }
    }

    // ---------- 3. Вебхуки приходят повторно и не по порядку ----------
    static void webhooks() throws Exception {
        System.out.println("== 3. webhooks: duplicate and out of order");
        long id = createPayment("shop-9", "order-7", 30_00);
        update("update payment set status = 'PENDING' where id = ?", id);
        PSP.charge("pay-" + id, id, 30_00);                     // PSP провёл платёж и шлёт вебхуки
        for (String event : List.of("payment.succeeded", "payment.succeeded", "payment.processing", "payment.failed")) {
            int changed = applyWebhook(id, event);
            System.out.printf("   %-20s rows changed %d, status %s%n", event, changed, status(id));
        }
        System.out.println("   ledger rows for payment " + id + ": " + scalar("select count(*) from ledger where payment_id = " + id));
    }

    // Переход разрешён только из допустимых состояний: финальный статус не откатывается назад.
    static int applyWebhook(long id, String event) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) {
            c.setAutoCommit(false);
            String sql = switch (event) {
                case "payment.succeeded" -> "update payment set status = 'SUCCEEDED' where id = ? and status = 'PENDING'";
                case "payment.failed" -> "update payment set status = 'FAILED' where id = ? and status = 'PENDING'";
                default -> "update payment set status = 'PENDING' where id = ? and status in ('CREATED')";
            };
            var u = c.prepareStatement(sql); u.setLong(1, id);
            int n = u.executeUpdate();
            if (n == 1 && event.equals("payment.succeeded")) post(c, id);
            c.commit();
            return n;
        }
    }

    // Двойная запись: деньги клиента приходят на транзитный счёт PSP и уходят в долг перед магазином.
    static void post(Connection c, long id) throws SQLException {
        var p = c.prepareStatement("""
            insert into ledger select id, 'psp_receivable', amount from payment where id = ?
            union all select id, 'merchant:' || merchant, -amount from payment where id = ?""");
        p.setLong(1, id); p.setLong(2, id); p.executeUpdate();
    }

    // ---------- 4. Сверка с файлом расчётов PSP ----------
    static void reconciliation() throws Exception {
        System.out.println("== 4. reconciliation against the PSP settlement file");
        // Платёж, по которому сервис упал сразу после вызова PSP; PSP его провёл.
        long orphan = createPayment("shop-7", "order-50", 20_00);
        update("update payment set status = 'PENDING' where id = ?", orphan);
        PSP.loseNextResponse = true;
        try { PSP.charge("pay-" + orphan, orphan, 20_00); } catch (SocketTimeoutException crashed) { }
        // Файл расчётов PSP за день: все его списания с нашими payment_id из метаданных.
        exec("drop table if exists settlement; create table settlement(psp_ref text, payment_id bigint, amount bigint)");
        try (Connection c = DriverManager.getConnection(URL)) {
            var ins = c.prepareStatement("insert into settlement values (?, ?, ?)");
            for (var ch : PSP.charges.values()) { ins.setString(1, ch.ref()); ins.setLong(2, ch.paymentId()); ins.setLong(3, ch.amount()); ins.addBatch(); }
            ins.executeBatch();
            // FULL JOIN: строки, которые есть только с одной стороны, и строки с разными суммами.
            ResultSet r = c.createStatement().executeQuery("""
                select coalesce(p.id, s.payment_id) as id, s.psp_ref, p.status, p.psp_ref as our_ref,
                       case when s.psp_ref is null then 'we have it, PSP does not'
                            when p.status is distinct from 'SUCCEEDED' then 'PSP settled, ours is ' || coalesce(p.status, 'missing')
                            when p.psp_ref <> s.psp_ref then 'extra charge, we know ' || p.psp_ref
                            else 'amount differs' end as issue
                from (select * from payment where status in ('SUCCEEDED', 'PENDING')) p
                full join settlement s on s.payment_id = p.id
                where s.psp_ref is null or p.id is null or p.status <> 'SUCCEEDED'
                   or p.psp_ref <> s.psp_ref or p.amount <> s.amount
                order by 1, 2""");
            System.out.println("   settlement rows: " + PSP.charges.size());
            while (r.next())
                System.out.printf("   payment %d, %s -> %s%n", r.getLong(1), r.getString(2), r.getString(5));
        }
        System.out.println("   ledger balance (must be 0): " + scalar("select coalesce(sum(amount), 0) from ledger"));
    }

    // ---------- PSP: модель провайдера ----------
    static class Psp {
        record Charge(String ref, long paymentId, long amount) {}
        final Map<String, Charge> charges = new LinkedHashMap<>();   // ключ идемпотентности -> списание
        boolean loseNextResponse;

        synchronized String charge(String key, long paymentId, long amount) throws SocketTimeoutException {
            // Повтор с тем же ключом возвращает то же списание, а не создаёт новое.
            Charge ch = charges.computeIfAbsent(key, k -> new Charge("psp-" + (charges.size() + 1), paymentId, amount));
            if (loseNextResponse) { loseNextResponse = false; throw new SocketTimeoutException("read timed out"); }
            return ch.ref();
        }
    }

    static String status(long id) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL);
             ResultSet r = c.createStatement().executeQuery("select status from payment where id = " + id)) {
            r.next(); return r.getString(1);
        }
    }

    static void update(String sql, long id) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, id); p.executeUpdate();
        }
    }

    static long scalar(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL); ResultSet r = c.createStatement().executeQuery(sql)) {
            r.next(); return r.getLong(1);
        }
    }

    static void exec(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) { exec(c, sql); }
    }

    static void exec(Connection c, String sql) throws SQLException {
        c.createStatement().execute(sql);
    }
}
