import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

// Запуск: Redis 8 на localhost:6379 (см. README), затем java -cp "$(cat target/classpath.txt)" NotificationDemo.java
// Очереди — списки Redis. Отправка через провайдера моделируется паузой 1 мс; с чтением из Redis один воркер
// успевает около 550 сообщений в секунду.
public class NotificationDemo {
    static final RedisClient redis = RedisClient.create("redis://localhost:6379");

    public static void main(String[] args) throws Exception {
        System.out.println("== campaign of 3000 marketing pushes is queued, then a login code (OTP) arrives");
        otpLatency(false);
        otpLatency(true);

        System.out.println("== the same event delivered 3 times (at-least-once)");
        dedup();

        System.out.println("== frequency cap: at most 2 marketing pushes per user per day");
        cap();
        redis.close();
    }

    static void otpLatency(boolean priority) throws Exception {
        redis.del("q:all", "q:high", "q:low");
        for (int i = 0; i < 3000; i++) redis.lpush(priority ? "q:low" : "q:all", "promo:" + i + ":" + System.currentTimeMillis());
        redis.lpush(priority ? "q:high" : "q:all", "otp:anna:" + System.currentTimeMillis());
        long otpDelay = -1; int sentBeforeOtp = 0;
        while (otpDelay < 0) {
            // Воркер всегда сначала смотрит в приоритетную очередь.
            List<String> m = priority ? redis.brpop(1, "q:high", "q:low") : redis.brpop(1, "q:all");
            String[] p = m.get(1).split(":");
            Thread.sleep(1);                                                   // «отправка»
            if (p[0].equals("otp")) otpDelay = System.currentTimeMillis() - Long.parseLong(p[2]);
            else sentBeforeOtp++;
        }
        System.out.printf("   %-26s OTP delivered after %5d ms, %4d promos sent before it%n",
                priority ? "separate priority queue:" : "one FIFO queue:", otpDelay, sentBeforeOtp);
    }

    static void dedup() {
        AtomicInteger sent = new AtomicInteger(), skipped = new AtomicInteger();
        redis.del("sent:payment-81:anna");
        for (int delivery = 0; delivery < 3; delivery++) {
            // Ключ — событие + получатель; хранится дольше окна повторной доставки.
            String ok = redis.set("sent:payment-81:anna", "1", SetParams.setParams().nx().ex(86400));
            if (ok != null) sent.incrementAndGet(); else skipped.incrementAndGet();
        }
        System.out.println("   pushes sent " + sent.get() + ", duplicates skipped " + skipped.get());
    }

    static void cap() {
        String key = "cap:anna:2026-10-03";
        redis.del(key);
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 1; i <= 4; i++) {
            long n = redis.incr(key);
            if (n == 1) redis.expire(key, 2 * 86400);
            sb.append(" promo-").append(i).append(n <= 2 ? ": sent" : ": dropped").append(';');
        }
        System.out.println(sb);
    }
}
