import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import redis.clients.jedis.RedisClient;

// Запуск: Redis 8 на localhost:6379 (см. README), затем
// java -cp "$(cat target/classpath.txt)" RateLimitDemo.java
// Лимит: 10 запросов в секунду на клиента. Время передаём в скрипты явно (мс),
// чтобы сценарий на границе окна был воспроизводимым; в сервисе это System.currentTimeMillis().
public class RateLimitDemo {
    static final RedisClient redis = RedisClient.create("redis://localhost:6379");
    static final int LIMIT = 10;
    static final long WINDOW_MS = 1000;

    // Фиксированное окно: счётчик на номер окна.
    static final String FIXED = """
        local n = redis.call('INCR', KEYS[1])
        if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
        return n <= tonumber(ARGV[2]) and 1 or 0
        """;

    // Скользящий журнал: время каждого запроса в ZSET, старые удаляем.
    static final String LOG = """
        local now, window, limit = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
        redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
        if redis.call('ZCARD', KEYS[1]) >= limit then return 0 end
        redis.call('ZADD', KEYS[1], now, ARGV[4])
        redis.call('PEXPIRE', KEYS[1], window)
        return 1
        """;

    // Скользящее окно-счётчик: текущее окно + предыдущее с весом его доли в скользящем окне.
    static final String SLIDING = """
        local now, window, limit = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
        local cur = math.floor(now / window)
        local prev = tonumber(redis.call('GET', KEYS[1] .. ':' .. (cur - 1)) or '0')
        local curN = tonumber(redis.call('GET', KEYS[1] .. ':' .. cur) or '0')
        local weight = 1 - (now % window) / window
        if prev * weight + curN >= limit then return 0 end
        redis.call('INCR', KEYS[1] .. ':' .. cur)
        redis.call('PEXPIRE', KEYS[1] .. ':' .. cur, window * 2)
        return 1
        """;

    // Token bucket: ёмкость, скорость пополнения; храним остаток и время последнего пополнения.
    static final String BUCKET = """
        local now, capacity, perMs = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
        local b = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
        local tokens, ts = tonumber(b[1]) or capacity, tonumber(b[2]) or now
        tokens = math.min(capacity, tokens + (now - ts) * perMs)
        local ok = 0
        if tokens >= 1 then tokens = tokens - 1; ok = 1 end
        redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
        redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / perMs))
        return ok
        """;

    interface Limiter { boolean allow(String client, long now, int seq); }

    public static void main(String[] args) throws Exception {
        redis.flushAll();
        System.out.println("== 10 requests at t=900 ms, 10 more at t=1100 ms; limit 10 per second");
        boundary("fixed window", (c, now, seq) -> eval(FIXED, "fw:" + c + ":" + now / WINDOW_MS, WINDOW_MS, LIMIT));
        boundary("sliding log", (c, now, seq) -> eval(LOG, "log:" + c, now, WINDOW_MS, LIMIT, now + "-" + seq));
        boundary("sliding counter", (c, now, seq) -> eval(SLIDING, "sw:" + c, now, WINDOW_MS, LIMIT));
        boundary("token bucket", (c, now, seq) -> eval(BUCKET, "tb:" + c, now, LIMIT, LIMIT / 1000.0));

        memory();
        race();
        redis.close();
    }

    static void boundary(String name, Limiter l) {
        int early = 0, late = 0;
        for (int i = 0; i < 10; i++) if (l.allow("anna", 900, i)) early++;
        for (int i = 0; i < 10; i++) if (l.allow("anna", 1100, 10 + i)) late++;
        System.out.printf("   %-16s allowed %2d + %2d = %2d within 200 ms%n", name, early, late, early + late);
    }

    // Сколько памяти занимает состояние клиента с лимитом 10 000 в минуту.
    static void memory() {
        System.out.println("== memory for one client, limit 10000 per minute, full window");
        for (int i = 0; i < 10_000; i++) eval(LOG, "mem:log", 1_000 + i, 60_000, 10_000, "r-" + i);
        for (int i = 0; i < 10_000; i++) eval(SLIDING, "mem:sw", 1_000 + i, 60_000, 10_000);
        for (int i = 0; i < 10_000; i++) eval(BUCKET, "mem:tb", 1_000 + i, 10_000, 10_000 / 60_000.0);
        System.out.println("   sliding log:     " + redis.memoryUsage("mem:log") + " bytes");
        long sw = 0;
        for (String k : redis.keys("mem:sw:*")) sw += redis.memoryUsage(k);
        System.out.println("   sliding counter: " + sw + " bytes");
        System.out.println("   token bucket:    " + redis.memoryUsage("mem:tb") + " bytes");
    }

    // 200 одновременных запросов при лимите 100: GET и SET отдельными командами против одного скрипта.
    static void race() throws Exception {
        System.out.println("== 200 concurrent requests, limit 100");
        redis.del("race:naive", "race:lua");
        int naive = burst(() -> {
            long n = Long.parseLong(redis.get("race:naive") == null ? "0" : redis.get("race:naive"));
            if (n >= 100) return false;
            redis.set("race:naive", String.valueOf(n + 1));     // между GET и SET успели другие
            return true;
        });
        int lua = burst(() -> eval(FIXED, "race:lua", 60_000, 100));
        System.out.println("   GET then SET:   allowed " + naive);   // счётчик в конце — от 11 до 37, от запуска к запуску
        System.out.println("   Lua INCR:       allowed " + lua + ", counter " + redis.get("race:lua"));
    }

    static int burst(Callable<Boolean> request) throws Exception {
        var pool = Executors.newFixedThreadPool(50);
        var start = new CountDownLatch(1);
        var allowed = new AtomicInteger();
        List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 200; i++)
            fs.add(pool.submit(() -> { start.await(); if (request.call()) allowed.incrementAndGet(); return null; }));
        start.countDown();
        for (var f : fs) f.get();
        pool.shutdown();
        return allowed.get();
    }

    static boolean eval(String script, String key, Object... args) {
        List<String> a = new ArrayList<>();
        for (Object o : args) a.add(String.valueOf(o));
        return ((Long) redis.eval(script, List.of(key), a)) == 1L;
    }
}
