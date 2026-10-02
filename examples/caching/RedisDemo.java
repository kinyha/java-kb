import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import redis.clients.jedis.RedisClient;


// Запуск: docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
//         java -cp "$(cat target/classpath.txt)" RedisDemo.java
public class RedisDemo {
    public static void main(String[] args) throws Exception {
        try (RedisClient r = RedisClient.create("redis://localhost:6379")) {
            r.flushAll();
            structures(r);
            atomicCounter(r);
            singleThread(r);
            pipelining(r);
            eviction(r);
        }
    }

    // ---------- структуры под задачи ----------
    static void structures(RedisClient r) {
        r.hset("session:s-1", java.util.Map.of("user", "anna", "role", "merchant", "shop", "m-7"));   // Хеш: объект по полям.
        r.expire("session:s-1", 1800);
        long now = 1_000;
        r.zadd("retry:payments", now + 30, "p-1");                   // Sorted set: очередь по времени.
        r.zadd("retry:payments", now + 5, "p-2");
        r.zadd("retry:payments", now + 60, "p-3");
        r.sadd("cards:m-7", "4111", "5500", "4111");                 // Множество: уникальные значения.
        System.out.println("== hash session: " + r.hgetAll("session:s-1") + ", ttl " + r.ttl("session:s-1"));
        System.out.println("== zset due by t=1040: " + r.zrange("retry:payments", redis.clients.jedis.params.ZRangeParams.zrangeByScoreParams(0, now + 40)));
        System.out.println("== set unique cards: " + r.scard("cards:m-7"));
    }

    // ---------- INCR атомарен, GET+SET — нет ----------
    static void atomicCounter(RedisClient r) throws Exception {
        for (boolean atomic : new boolean[] {false, true}) {
            r.set("limit:m-7", "0");
            var pool = Executors.newFixedThreadPool(8);
            var done = new CountDownLatch(8);
            for (int t = 0; t < 8; t++) pool.submit(() -> {
                for (int i = 0; i < 250; i++) {
                    if (atomic) r.incr("limit:m-7");
                    else r.set("limit:m-7", String.valueOf(Long.parseLong(r.get("limit:m-7")) + 1));  // Прочитать, прибавить, записать.
                }
                done.countDown();
            });
            done.await();
            pool.shutdown();
            System.out.println("== 8 threads x 250 increments, " + (atomic ? "INCR" : "GET+SET") + ": " + r.get("limit:m-7"));
        }
    }

    // ---------- один поток: медленная команда задерживает всех ----------
    static void singleThread(RedisClient r) throws Exception {
        try (RedisClient other = RedisClient.create("redis://localhost:6379")) {
            other.ping();
            long t = System.nanoTime();
            other.ping();
            System.out.printf("== PING alone: %.2f ms%n", (System.nanoTime() - t) / 1e6);
            long[] scriptMs = new long[1];
            Thread slow = Thread.ofPlatform().start(() -> {           // Lua-скрипт: счётный цикл внутри Redis.
                long s = System.nanoTime();
                r.eval("local x = 0 for i = 1, 150000000 do x = x + i end return 1");
                scriptMs[0] = (System.nanoTime() - s) / 1_000_000;
            });
            Thread.sleep(20);
            t = System.nanoTime();
            other.ping();
            long pingMs = (System.nanoTime() - t) / 1_000_000;
            slow.join();
            System.out.println("== PING during a " + scriptMs[0] + " ms script: " + pingMs + " ms");
        }
        for (int i = 0; i < 200_000; i++) if (i % 50_000 == 0) r.sadd("big", fill(i));
        System.out.println("   big set size " + r.scard("big"));
        long t = System.nanoTime();
        r.del("big");
        System.out.printf("== DEL of 200k-member set: %.1f ms%n", (System.nanoTime() - t) / 1e6);
        for (int i = 0; i < 200_000; i += 50_000) r.sadd("big", fill(i));
        t = System.nanoTime();
        r.unlink("big");
        System.out.printf("== UNLINK of the same set: %.1f ms%n", (System.nanoTime() - t) / 1e6);
    }

    static String[] fill(int from) {
        String[] a = new String[50_000];
        for (int i = 0; i < a.length; i++) a[i] = "member-" + (from + i);
        return a;
    }

    // ---------- pipelining: меньше кругов по сети ----------
    static void pipelining(RedisClient r) {
        int n = 10_000;
        long t = System.nanoTime();
        for (int i = 0; i < n; i++) r.set("k:" + i, "v");
        double plain = (System.nanoTime() - t) / 1e6;
        t = System.nanoTime();
        try (var p = r.pipelined()) {
            for (int i = 0; i < n; i++) p.set("k:" + i, "v");
            p.sync();
        }
        double piped = (System.nanoTime() - t) / 1e6;
        System.out.printf("== %d SETs: one by one %.0f ms, pipelined %.0f ms%n", n, plain, piped);
    }

    // ---------- maxmemory и вытеснение ----------
    static void eviction(RedisClient r) {
        r.flushAll();
        r.configSet("maxmemory", "4mb");
        for (String policy : List.of("noeviction", "allkeys-lru")) {
            r.flushAll();
            r.configSet("maxmemory-policy", policy);
            String value = "x".repeat(1000);
            int written = 0;
            String error = null;
            try {
                for (int i = 0; i < 20_000; i++) { r.set("cache:" + i, value); written++; }
            } catch (Exception e) {
                error = e.getMessage();
            }
            System.out.println("== maxmemory 4mb, " + policy + ": wrote " + written + ", keys now " + r.dbSize()
                    + (error == null ? "" : ", error: " + error));
        }
        r.configSet("maxmemory", "0");
        r.configSet("maxmemory-policy", "noeviction");
    }
}
