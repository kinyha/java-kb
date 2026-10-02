import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

// Запуск: docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
//         java -cp "$(cat target/classpath.txt)" CachingDemo.java
// Redis настоящий; «база» — заглушка с задержкой 20 мс и счётчиком запросов.
public class CachingDemo {
    static final RedisClient redis = RedisClient.create("redis://localhost:6379");

    static final Map<String, String> db = new ConcurrentHashMap<>(Map.of("tariff:m-7", "2.0%"));
    static final AtomicInteger dbReads = new AtomicInteger();

    static String dbRead(String key) {
        dbReads.incrementAndGet();
        sleep(20);
        return db.get(key);
    }

    public static void main(String[] args) throws Exception {
        redis.flushAll();
        cacheAside();
        staleAfterDelete();
        stampede(false);
        stampede(true);
        redis.close();
    }

    // ---------- cache-aside ----------
    static String get(String key, long ttlSec) {
        String v = redis.get(key);
        if (v != null) return v;                              // Попадание.
        v = dbRead(key);                                      // Промах: читаем базу...
        redis.set(key, v, SetParams.setParams().ex(ttlSec));  // ...и кладём в кеш со сроком.
        return v;
    }

    static void cacheAside() {
        for (int i = 1; i <= 3; i++) {
            long t = System.nanoTime();
            String v = get("tariff:m-7", 60);
            System.out.printf("== cache-aside read %d: %s in %.1f ms, db reads %d%n", i, v, (System.nanoTime() - t) / 1e6, dbReads.get());
        }
    }

    // ---------- гонка: читатель кладёт старое значение после удаления ----------
    static void staleAfterDelete() throws Exception {
        redis.del("tariff:m-7");
        CountDownLatch readerLoaded = new CountDownLatch(1), writerDone = new CountDownLatch(1);
        Thread reader = Thread.ofPlatform().start(() -> {
            String v = dbRead("tariff:m-7");                  // Читатель прочитал 2.0% из базы...
            readerLoaded.countDown();
            await(writerDone);                                // ...задержался (GC, сеть)...
            redis.set("tariff:m-7", v, SetParams.setParams().ex(3600));  // ...и положил старое значение.
        });
        await(readerLoaded);
        db.put("tariff:m-7", "1.5%");                         // Писатель обновил базу
        redis.del("tariff:m-7");                              // и удалил ключ — как положено.
        writerDone.countDown();
        reader.join();
        System.out.println("== race: db=" + db.get("tariff:m-7") + ", cache=" + redis.get("tariff:m-7")
                + ", ttl=" + redis.ttl("tariff:m-7") + " s");
    }

    // ---------- stampede: популярный ключ истёк под нагрузкой ----------
    static void stampede(boolean withLock) throws Exception {
        redis.del("tariff:m-7");
        dbReads.set(0);
        int clients = 50;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(clients);
        List<java.util.concurrent.Future<String>> fs = new ArrayList<>();
        for (int i = 0; i < clients; i++) fs.add(pool.submit(() -> { start.await(); return withLock ? getWithLock("tariff:m-7") : get("tariff:m-7", 60); }));
        long t = System.nanoTime();
        start.countDown();
        for (var f : fs) f.get();
        pool.shutdown();
        System.out.printf("== stampede, %d concurrent misses, %s: db reads %d, all answered in %.0f ms%n",
                clients, withLock ? "SET NX lock" : "no protection", dbReads.get(), (System.nanoTime() - t) / 1e6);
    }

    // Один загружает, остальные ждут значения в кеше.
    static String getWithLock(String key) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String v = redis.get(key);
            if (v != null) return v;
            String token = Thread.currentThread().getName();
            if ("OK".equals(redis.set("lock:" + key, token, SetParams.setParams().nx().px(2000)))) {   // Только один получит.
                try {
                    v = redis.get(key);                        // Перепроверить: пока ждали, могли загрузить.
                    if (v != null) return v;
                    v = dbRead(key);
                    redis.set(key, v, SetParams.setParams().ex(60));
                    return v;
                } finally {
                    redis.del("lock:" + key);
                }
            }
            sleep(5);                                          // Кто-то уже грузит: подождать и проверить кеш.
        }
        return dbRead(key);                                    // Запасной путь, если загрузчик завис.
    }

    static void await(CountDownLatch l) {
        try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
