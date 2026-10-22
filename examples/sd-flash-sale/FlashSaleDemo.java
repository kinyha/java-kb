import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ZRangeParams;
import redis.clients.jedis.resps.StreamEntry;

// Запуск: PostgreSQL 17 на localhost:55439 и Redis 8 на localhost:6389 (см. README), затем
// java -cp "$(cat target/classpath.txt)" FlashSaleDemo.java [race] [throughput] [gate] [holds] [queue]
// Демо пересоздаёт свои таблицы и очищает Redis командой FLUSHALL.
public class FlashSaleDemo {
    static final String URL = "jdbc:postgresql://localhost:55439/postgres?user=postgres&password=pw";
    static RedisClient redis;

    interface Buy { boolean buy(Connection c, int user) throws Exception; }

    public static void main(String[] args) throws Exception {
        var pool = new ConnectionPoolConfig();
        pool.setMaxTotal(300); pool.setMaxIdle(300);
        redis = RedisClient.builder().hostAndPort("localhost", 6389).poolConfig(pool).build();
        List<String> run = args.length == 0 ? List.of("race", "throughput", "gate", "holds", "queue") : List.of(args);
        if (run.contains("race")) race();
        if (run.contains("throughput")) throughput();
        if (run.contains("gate")) gate();
        if (run.contains("holds")) holds();
        if (run.contains("queue")) queue();
        redis.close();
    }

    // ---------- стратегии покупки одной единицы товара ----------

    // Наивно: прочитать остаток, проверить в Java, записать новое значение.
    static boolean naive(Connection c, int user) throws SQLException {
        int stock = intQuery(c, "select stock from item where id = 1");
        if (stock <= 0) { c.rollback(); return false; }
        exec(c, "update item set stock = " + (stock - 1) + " where id = 1");
        order(c, user, 0);
        c.commit();
        return true;
    }

    // Условный UPDATE: проверка и уменьшение — одна операция под блокировкой строки.
    static boolean conditional(Connection c, int user) throws SQLException {
        if (exec(c, "update item set stock = stock - 1 where id = 1 and stock > 0") == 0) { c.rollback(); return false; }
        order(c, user, 0);                       // блокировка строки item держится до commit
        c.commit();
        return true;
    }

    // То же, но горячий UPDATE — последним: блокировка держится только UPDATE + COMMIT.
    static boolean conditionalLast(Connection c, int user) throws SQLException {
        order(c, user, 0);
        if (exec(c, "update item set stock = stock - 1 where id = 1 and stock > 0") == 0) { c.rollback(); return false; }
        c.commit();
        return true;
    }

    // Под блокировкой ещё 2 мс работы: вызов сервиса цен или лимитов внутри транзакции.
    static boolean conditionalSlow(Connection c, int user) throws Exception {
        if (exec(c, "update item set stock = stock - 1 where id = 1 and stock > 0") == 0) { c.rollback(); return false; }
        Thread.sleep(2);
        order(c, user, 0);
        c.commit();
        return true;
    }

    // Строка на каждую единицу товара: каждый берёт первую свободную, занятые пропускает.
    static boolean skipLocked(Connection c, int user) throws SQLException {
        ResultSet r = c.createStatement().executeQuery("""
            update unit set buyer = %d where id = (
              select id from unit where buyer is null order by id limit 1 for update skip locked)
            returning id""".formatted(user));
        if (!r.next()) { c.rollback(); return false; }   // пусто = свободных незаблокированных нет
        order(c, user, r.getInt(1));
        c.commit();
        return true;
    }

    // Остаток разбит на 16 строк-корзин: начинаем со случайной, пустые обходим.
    static boolean buckets(Connection c, int user) throws SQLException {
        int start = ThreadLocalRandom.current().nextInt(16);
        for (int i = 0; i < 16; i++) {
            int b = (start + i) % 16;
            if (exec(c, "update bucket set stock = stock - 1 where b = " + b + " and stock > 0") == 1) {
                order(c, user, 0);
                c.commit();
                return true;
            }
        }
        c.rollback();
        return false;
    }

    // Redis выдаёт номер единицы атомарно; база только вставляет заказ.
    static final String TAKE = """
        local left = tonumber(redis.call('GET', KEYS[1]))
        if left <= 0 then return 0 end
        return redis.call('DECR', KEYS[1]) + 1      -- номер единицы: N, N-1, ..., 1
        """;

    static boolean redisTake(Connection c, int user) throws SQLException {
        long unit = (Long) redis.eval(TAKE, 1, "stock");
        if (unit == 0) return false;
        order(c, user, (int) unit);
        c.commit();
        return true;
    }

    static void order(Connection c, int user, int unit) throws SQLException {
        exec(c, "insert into orders(sale, user_id, unit_no) values (1, " + user + ", " + unit + ")");
    }

    // ---------- 1. гонка за последние 100 единиц ----------

    static void race() throws Exception {
        System.out.println("== 1. 2000 buyers, 64 threads, 100 units in stock");
        raceOne("read, check, write", FlashSaleDemo::naive, true);
        raceOne("conditional update", FlashSaleDemo::conditional, true);
        raceOne("skip locked units", FlashSaleDemo::skipLocked, false);
        raceOne("16 buckets", FlashSaleDemo::buckets, false);
        raceOne("redis Lua take", FlashSaleDemo::redisTake, false);
    }

    static void raceOne(String name, Buy buy, boolean usesItemRow) throws Exception {
        reset(100);
        AtomicInteger next = new AtomicInteger();
        runThreads(64, c -> {
            int u;
            while ((u = next.getAndIncrement()) < 2000) attempt(buy, c, u);
        });
        int orders = intQuery("select count(*) from orders");
        System.out.printf("   %-20s orders %4d, oversold %4d%s%n", name, orders, Math.max(0, orders - 100),
            usesItemRow ? ", item.stock = " + intQuery("select stock from item") : "");
    }

    // ---------- 2. пропускная способность: товар не кончается ----------

    static void throughput() throws Exception {
        System.out.println("== 2. orders per second while stock lasts (3 s window after 1 s warm-up)");
        System.out.println("   strategy             threads   orders/s   avg latency   waiting on a lock");
        for (int t : new int[] {1, 8, 64, 256}) measure("conditional update", FlashSaleDemo::conditional, t);
        measure("  hot update last", FlashSaleDemo::conditionalLast, 64);
        measure("  +2 ms under lock", FlashSaleDemo::conditionalSlow, 64);
        measure("skip locked units", FlashSaleDemo::skipLocked, 64);
        measure("16 buckets", FlashSaleDemo::buckets, 64);
        measure("redis Lua take", FlashSaleDemo::redisTake, 64);
        measure("  Lua only, no DB", (c, u) -> (Long) redis.eval(TAKE, 1, "stock") > 0, 64);
        try (Connection c = DriverManager.getConnection(URL)) {
            reset(10_000_000);
            runThreadsFor(8, 2000, b -> attempt(FlashSaleDemo::conditional, b, 1));
            ResultSet r = c.createStatement().executeQuery(
                "select n_tup_upd, n_tup_hot_upd, n_dead_tup from pg_stat_user_tables where relname = 'item'");
            r.next();
            System.out.printf("   item row after 2 s: updates %d, HOT %d, dead versions %d%n", r.getLong(1), r.getLong(2), r.getLong(3));
        }
    }

    static void measure(String name, Buy buy, int threads) throws Exception {
        reset(10_000_000);
        AtomicLong done = new AtomicLong();
        AtomicBoolean measuring = new AtomicBoolean();
        double[] waiting = new double[1];
        long[] window = new long[2];
        runThreadsFor(threads, 4000, c -> {
            if (attempt(buy, c, 1)) done.incrementAndGet();
        }, () -> {                                   // сэмплер: сколько соединений ждут блокировку
            try (Connection s = DriverManager.getConnection(URL)) {
                Thread.sleep(1000);
                window[0] = done.get();
                long t0 = System.nanoTime();
                int samples = 0, sum = 0;
                while (System.nanoTime() - t0 < 3_000_000_000L) {
                    sum += intQuery(s, """
                        select count(*) from pg_stat_activity where application_name = 'buyer'
                          and (wait_event_type = 'Lock' or wait_event = 'LockManager')""");
                    samples++;
                    Thread.sleep(50);
                }
                window[1] = done.get() - window[0];
                waiting[0] = (double) sum / samples;
            }
            return null;
        });
        double perSec = window[1] / 3.0;
        System.out.printf("   %-20s %7d %10.0f %10.1f ms %12.1f of %d%n",
            name, threads, perSec, threads * 1000.0 / perSec, waiting[0], threads);
    }

    // ---------- 3. Redis как привратник, база — последний рубеж ----------

    // Один заказ на пользователя: повторный клик возвращает уже выданную единицу.
    static final String BUY = """
        local unit = redis.call('HGET', KEYS[2], ARGV[1])
        if unit then return {'DUP', tonumber(unit)} end
        local left = tonumber(redis.call('GET', KEYS[1]))
        if left <= 0 then return {'SOLD_OUT', 0} end
        unit = redis.call('DECR', KEYS[1]) + 1
        redis.call('HSET', KEYS[2], ARGV[1], unit)
        redis.call('XADD', KEYS[3], '*', 'user', ARGV[1], 'unit', unit)   -- заявка для записи в базу
        return {'OK', unit}
        """;

    static void gate() throws Exception {
        System.out.println("== 3. 2000 users click 1-3 times at once, 500 units");
        List<Integer> clicks = new ArrayList<>();
        Random rnd = new Random(42);
        for (int u = 1; u <= 2000; u++) for (int k = 1 + rnd.nextInt(3); k > 0; k--) clicks.add(u);
        Collections.shuffle(clicks, rnd);

        redis.flushAll();
        redis.set("stock", "500");
        Map<Long, Integer> unitOwner = new ConcurrentHashMap<>();
        AtomicInteger ok = new AtomicInteger();
        parallel(clicks, u -> {
            long unit = (Long) redis.eval(TAKE, 1, "stock");
            if (unit > 0) { ok.incrementAndGet(); unitOwner.put(unit, u); }
        });
        System.out.printf("   plain take       requests %d, ok %d, units went to %d users%n",
            clicks.size(), ok.get(), new HashSet<>(unitOwner.values()).size());

        redis.flushAll();
        redis.set("stock", "500");
        Map<String, AtomicInteger> outcome = new ConcurrentSkipListMap<>();
        parallel(clicks, u -> {
            List<?> r = (List<?>) redis.eval(BUY, List.of("stock", "buyers", "orders"), List.of(String.valueOf(u)));
            outcome.computeIfAbsent((String) r.get(0), k -> new AtomicInteger()).incrementAndGet();
        });
        System.out.printf("   Lua buy          requests %d, %s, buyers %d%n", clicks.size(), outcome, redis.hlen("buyers"));

        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                drop table if exists orders;
                create table orders(sale int, user_id int, unit_no int check (unit_no between 1 and 500),
                                    unique (sale, unit_no), unique (sale, user_id));
                """);
        }
        String last = writer("order writer    ", "-");

        System.out.println("== 4. failover: the new master missed the last 40 writes");
        List<StreamEntry> tail = redis.xrevrange("orders", "+", "-", 40);
        for (StreamEntry e : tail) {                  // эмуляция: реплика не получила 40 последних покупок
            redis.hdel("buyers", e.getFields().get("user"));
            redis.xdel("orders", e.getID());
        }
        redis.incrBy("stock", 40);
        System.out.printf("   after failover   stock %s, buyers %d%n", redis.get("stock"), redis.hlen("buyers"));
        List<Integer> more = new ArrayList<>();
        for (StreamEntry e : tail) more.add(Integer.parseInt(e.getFields().get("user")));   // «прошёл ли заказ?» — жмут снова
        for (int u = 2001; u <= 2260; u++) more.add(u);
        Collections.shuffle(more, rnd);
        AtomicInteger again = new AtomicInteger();
        parallel(more, u -> {
            List<?> r = (List<?>) redis.eval(BUY, List.of("stock", "buyers", "orders"), List.of(String.valueOf(u)));
            if (r.get(0).equals("OK")) again.incrementAndGet();
        });
        System.out.printf("   %d more requests: ok %d%n", more.size(), again.get());
        writer("order writer    ", last);
        reconcile();
    }

    // Читает заявки из потока пачками по 100 и вставляет пачку одной транзакцией.
    static String writer(String label, String after) throws SQLException {
        int batches = 0, inserted = 0, rejected = 0;
        try (Connection c = DriverManager.getConnection(URL)) {
            c.setAutoCommit(false);
            var ins = c.prepareStatement("insert into orders values (1, ?, ?) on conflict do nothing");
            List<StreamEntry> batch;
            while (!(batch = redis.xrange("orders", after, "+", 100)).isEmpty()) {
                for (StreamEntry e : batch) {
                    ins.setInt(1, Integer.parseInt(e.getFields().get("user")));
                    ins.setInt(2, Integer.parseInt(e.getFields().get("unit")));
                    ins.addBatch();
                }
                for (int n : ins.executeBatch()) if (n == 1) inserted++; else rejected++;
                c.commit();                            // одна транзакция на 100 заказов
                batches++;
                after = "(" + batch.get(batch.size() - 1).getID();   // дальше — строго после последней
            }
        }
        System.out.printf("   %s batches %d, inserted %d, rejected by unique %d, orders in DB %d%n",
            label, batches, inserted, rejected, intQuery("select count(*) from orders"));
        return after;
    }

    // Сверка: кому Redis сказал «да», и у кого есть заказ в базе.
    static void reconcile() throws SQLException {
        Set<String> inRedis = redis.hgetAll("buyers").keySet();
        Set<String> inDb = new HashSet<>();
        try (Connection c = DriverManager.getConnection(URL)) {
            ResultSet r = c.createStatement().executeQuery("select user_id from orders");
            while (r.next()) inDb.add(r.getString(1));
        }
        Set<String> redisOnly = new HashSet<>(inRedis); redisOnly.removeAll(inDb);
        Set<String> dbOnly = new HashSet<>(inDb); dbOnly.removeAll(inRedis);
        System.out.printf("   reconcile        Redis buyers %d, DB orders %d, Redis-only %d, DB-only %d%n",
            inRedis.size(), inDb.size(), redisOnly.size(), dbOnly.size());
        // Источник истины — база: Redis пересобирается из неё.
        for (String u : redisOnly) redis.hdel("buyers", u);
        try (Connection c = DriverManager.getConnection(URL)) {
            ResultSet r = c.createStatement().executeQuery("select user_id, unit_no from orders");
            while (r.next()) redis.hset("buyers", r.getString(1), r.getString(2));
        }
        redis.set("stock", String.valueOf(500 - inDb.size()));
        System.out.printf("   rebuilt from DB  stock %s, buyers %d%n", redis.get("stock"), redis.hlen("buyers"));
    }

    // ---------- 5. временный резерв: оплата на границе срока против очистки ----------

    static final String RESERVE = """
        if tonumber(redis.call('GET', KEYS[1])) <= 0 then return 0 end
        redis.call('DECR', KEYS[1])
        local t = redis.call('TIME')
        redis.call('ZADD', KEYS[2], t[1] * 1000 + math.floor(t[2] / 1000) + tonumber(ARGV[2]), ARGV[1])
        return 1
        """;
    // Оплата засчитывается, только если резерв ещё наш и срок не прошёл — по часам Redis.
    static final String PAY = """
        local deadline = redis.call('ZSCORE', KEYS[1], ARGV[1])
        local t = redis.call('TIME')
        if not deadline or tonumber(deadline) < t[1] * 1000 + math.floor(t[2] / 1000) then return 0 end
        redis.call('ZREM', KEYS[1], ARGV[1])
        redis.call('SADD', KEYS[2], ARGV[1])
        return 1
        """;
    // Очистка: вернуть единицу в остаток только за тот резерв, который удалила сама.
    static final String SWEEP = """
        local t = redis.call('TIME')
        local now = t[1] * 1000 + math.floor(t[2] / 1000)
        local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now, 'LIMIT', 0, 100)
        for _, user in ipairs(expired) do
          if redis.call('ZREM', KEYS[1], user) == 1 then redis.call('INCR', KEYS[2]) end
        end
        return #expired
        """;

    static void holds() throws Exception {
        System.out.println("== 5. 1000 holds for 300 ms, 600 buyers pay around the deadline");
        holdsOne("naive sweeper", false);
        holdsOne("atomic Lua", true);
    }

    static void holdsOne(String name, boolean atomic) throws Exception {
        redis.flushAll();
        redis.set("stock", "1000");
        for (int u = 1; u <= 1000; u++) redis.eval(RESERVE, List.of("stock", "holds"), List.of(String.valueOf(u), "300"));
        long skew = (Long) redis.eval("local t = redis.call('TIME') return t[1] * 1000 + math.floor(t[2] / 1000)")
            - System.currentTimeMillis();              // сдвиг часов Redis относительно наших
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger late = new AtomicInteger();
        Thread sweeper = new Thread(() -> {
            while (!stop.get()) {
                if (atomic) redis.eval(SWEEP, List.of("holds", "stock"), List.of());
                else for (String u : redis.zrange("holds", ZRangeParams.zrangeByScoreParams(0, System.currentTimeMillis() + skew))) {
                    redis.incr("stock");               // вернуть единицу...
                    redis.zrem("holds", u);            // ...и только потом удалить резерв
                }
                try { Thread.sleep(5); } catch (InterruptedException e) { return; }
            }
        });
        sweeper.start();
        // 600 покупателей платят в случайный момент от -30 до +30 мс относительно срока.
        Random rnd = new Random(7);
        List<long[]> pays = new ArrayList<>();
        for (int u = 1; u <= 600; u++) {
            long deadline = (long) (double) redis.zscore("holds", String.valueOf(u)) - skew;
            pays.add(new long[] {u, deadline - 30 + rnd.nextInt(61)});
        }
        pays.sort(Comparator.comparingLong(p -> p[1]));
        parallel(pays, p -> {
            long wait = p[1] - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);
            String u = String.valueOf(p[0]);
            if (atomic) {
                if ((Long) redis.eval(PAY, List.of("holds", "paid"), List.of(u)) == 0) late.incrementAndGet();
            } else if (redis.zscore("holds", u) != null) {   // резерв на месте — значит, наш
                redis.sadd("paid", u);
                redis.zrem("holds", u);
            } else late.incrementAndGet();
        });
        Thread.sleep(400);
        stop.set(true);
        sweeper.join();
        long paid = redis.scard("paid"), stock = Long.parseLong(redis.get("stock")), held = redis.zcard("holds");
        System.out.printf("   %-14s paid %d, late %d, back in stock %d, held %d -> units %d of 1000, sold twice %d%n",
            name, paid, late.get(), stock, held, paid + stock + held, paid + stock + held - 1000);
    }

    // ---------- 6. очередь ожидания: модель допуска, шаг 10 секунд ----------

    // 300 000 человек в очереди, 10 000 единиц. В оформлении одновременно не больше 2 000 человек.
    // Из допущенных 70 % берут резерв, из резервов 85 % оплачивают, остальные пропадают
    // и через 2 минуты (12 шагов) единица возвращается в остаток.
    static void queue() {
        System.out.println("== 6. waiting room model: 300000 in queue, 10000 units, checkout fits 2000 users");
        System.out.println("   time   admitted   in stock   held   sold   waiting");
        Random rnd = new Random(1);
        int waiting = 300_000, stock = 10_000, sold = 0, admitted = 0;
        int[] expiring = new int[400], paying = new int[400];
        int firstZero = -1, admittedAtZero = 0, waves = 0;
        boolean admitting = true;
        for (int t = 0; ; t++) {
            stock += expiring[t];                       // брошенные резервы вернулись
            sold += paying[t];
            // Допуск: не больше мест в оформлении и не больше, чем нужно на оставшийся товар.
            int admit = Math.min(Math.min(2000, waiting), (int) Math.ceil(stock / 0.7));
            waiting -= admit; admitted += admit;
            if (firstZero >= 0 && admit > 0 && !admitting) waves++;
            admitting = admit > 0;
            for (int i = 0; i < admit && stock > 0; i++) {
                if (rnd.nextDouble() >= 0.7) continue;  // посмотрел и ушёл
                stock--;
                if (rnd.nextDouble() < 0.85) paying[t + 1]++; else expiring[t + 12]++;
            }
            int held = 0;
            for (int k = t + 1; k < t + 13; k++) held += expiring[k] + paying[k];
            if (t <= 8) System.out.printf("   %4ds %10d %10d %6d %6d %9d%n", t * 10, admitted, stock, held, sold, waiting);
            if (stock == 0 && firstZero < 0) { firstZero = t; admittedAtZero = admitted; }
            if (stock == 0 && held == 0) {
                System.out.printf("   stock first hit 0 at %ds; returned holds let in %d more users in %d waves%n",
                    firstZero * 10, admitted - admittedAtZero, waves);
                System.out.printf("   sold out for good at %ds: %d users get the answer without reaching checkout%n", t * 10, waiting);
                return;
            }
        }
    }

    // ---------- обвязка ----------

    static boolean attempt(Buy buy, Connection c, int user) {
        try { return buy.buy(c, user); }
        catch (Exception e) { try { c.rollback(); } catch (SQLException ignored) { } return false; }
    }

    static void reset(int stock) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) {
            exec(c, """
                drop table if exists item, unit, bucket, orders;
                create table item(id int primary key, stock int not null check (stock >= 0));
                insert into item values (1, %1$d);
                create table bucket(b int primary key, stock int not null check (stock >= 0));
                insert into bucket select g, %1$d / 16 + case when g < %1$d %% 16 then 1 else 0 end from generate_series(0, 15) g;
                create table unit(id int primary key, buyer int);
                insert into unit select g, null from generate_series(1, least(%1$d, 400000)) g;
                create index unit_free on unit(id) where buyer is null;
                create table orders(id bigserial primary key, sale int, user_id int, unit_no int);
                create unique index orders_unit on orders(sale, unit_no) where unit_no > 0;
                """.formatted(stock));
            exec(c, "vacuum analyze item, bucket, unit");
        }
        redis.flushAll();
        redis.set("stock", String.valueOf(stock));
    }

    interface ConnTask { void run(Connection c) throws Exception; }
    interface Task<T> { void run(T t) throws Exception; }

    // Каждый поток со своим соединением; все стартуют одновременно.
    static void runThreads(int threads, ConnTask body) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads), go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) fs.add(ex.submit(() -> {
            try (Connection c = DriverManager.getConnection(URL + "&ApplicationName=buyer")) {
                c.setAutoCommit(false);
                ready.countDown(); go.await();
                body.run(c);
            }
            return null;
        }));
        ready.await(); go.countDown();
        for (Future<?> f : fs) f.get();
        ex.shutdown();
    }

    // Потоки повторяют body, пока main не поднимет флаг stop: время на каждой операции не меряем.
    static void runThreadsFor(int threads, long millis, ConnTask body) throws Exception {
        runThreadsFor(threads, millis, body, () -> { Thread.sleep(millis); return null; });
    }

    static void runThreadsFor(int threads, long millis, ConnTask body, Callable<Void> whileRunning) throws Exception {
        AtomicBoolean stop = new AtomicBoolean();
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads), go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) fs.add(ex.submit(() -> {
            try (Connection c = DriverManager.getConnection(URL + "&ApplicationName=buyer")) {
                c.setAutoCommit(false);
                ready.countDown(); go.await();
                while (!stop.get()) body.run(c);
            }
            return null;
        }));
        ready.await(); go.countDown();
        whileRunning.call();
        stop.set(true);
        for (Future<?> f : fs) f.get();
        ex.shutdown();
    }

    static <T> void parallel(List<T> items, Task<T> body) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(64);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (T t : items) fs.add(ex.submit(() -> { go.await(); body.run(t); return null; }));
        go.countDown();
        for (Future<?> f : fs) f.get();
        ex.shutdown();
    }

    static int exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute(sql); return s.getUpdateCount(); }
    }

    static int intQuery(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getInt(1); }
    }

    static int intQuery(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(URL)) { return intQuery(c, sql); }
    }
}
