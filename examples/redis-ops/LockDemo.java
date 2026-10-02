import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

// Запуск: docker run -d --name kb-redis -p 6379:6379 redis:8-alpine
//         java -cp "$(cat target/classpath.txt)" LockDemo.java
public class LockDemo {
    static final RedisClient redis = RedisClient.create("redis://localhost:6379");
    static final Set<String> holders = ConcurrentHashMap.newKeySet();   // Кто сейчас «в критической секции».
    static final String LOCK = "lock:payout:m-7";

    static boolean acquire(String owner, long ttlMs) {
        return "OK".equals(redis.set(LOCK, owner, SetParams.setParams().nx().px(ttlMs)));
    }

    // Освободить, только если блокировка всё ещё наша: сравнение и удаление атомарно.
    static final String RELEASE = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";

    public static void main(String[] args) throws Exception {
        redis.del(LOCK);
        scenario(false);
        redis.del(LOCK);
        scenario(true);
        fencing();
        redis.close();
    }

    static void scenario(boolean safeRelease) throws Exception {
        holders.clear();
        System.out.println("== release with " + (safeRelease ? "Lua compare-and-delete" : "plain DEL"));
        acquire("A", 100);
        holders.add("A");
        Thread.sleep(200);                                   // A «замер»: пауза GC дольше TTL.
        holders.remove("A");                                 // (A ещё думает, что держит блокировку.)
        System.out.println("B acquires: " + acquire("B", 5000));
        holders.add("B");
        long released = safeRelease ? (Long) redis.eval(RELEASE, List.of(LOCK), List.of("A")) : redis.del(LOCK);
        System.out.println("A wakes up and releases: deleted " + released);
        boolean c = acquire("C", 5000);
        if (c) holders.add("C");
        System.out.println("C acquires: " + c + " -> inside critical section: " + holders);
    }

    // ---------- fencing token: хранилище отвергает запись старого владельца ----------
    static long lastToken = 0;
    static String storageWrite(long token, String who) {
        if (token < lastToken) return who + " write with token " + token + " rejected (last " + lastToken + ")";
        lastToken = token;
        return who + " write with token " + token + " accepted";
    }

    static void fencing() throws Exception {
        System.out.println("== fencing token");
        redis.del(LOCK, "fence:payout:m-7");
        acquire("A", 100);
        long tokenA = redis.incr("fence:payout:m-7");        // Номер выдаётся вместе с блокировкой.
        Thread.sleep(200);                                   // A замер, блокировка истекла.
        acquire("B", 5000);
        long tokenB = redis.incr("fence:payout:m-7");
        System.out.println(storageWrite(tokenB, "B"));
        System.out.println(storageWrite(tokenA, "A"));       // A проснулся и пишет со старым номером.
    }
}
