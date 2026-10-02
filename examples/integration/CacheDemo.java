package kb.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;

// Запуск: ./run.sh CacheDemo            — ConcurrentMapCacheManager (кеш по умолчанию)
//         ./run.sh CacheDemo caffeine   — Caffeine с ограничением размера и TTL
public class CacheDemo {

    // Изменяемый объект нарочно: так видно, что кеш хранит ссылку.
    static class Tariff {
        final String bank;
        List<String> fees = new ArrayList<>(List.of("1.5%"));
        Tariff(String bank) { this.bank = bank; }
        public String toString() { return bank + " " + fees; }
    }

    @Service
    static class TariffService {
        final AtomicInteger calls = new AtomicInteger();

        // Читаем через метод: поле прокси CGLIB не инициализировано (null).
        public int calls() { return calls.get(); }

        @Cacheable("tariffs") // Ключ — аргумент bank.
        public Tariff tariff(String bank) {
            calls.incrementAndGet();
            sleep(200); // Медленный запрос к банку.
            return new Tariff(bank);
        }

        @Cacheable(cacheNames = "tariffs-sync", sync = true) // Один поток считает, остальные ждут.
        public Tariff tariffSync(String bank) {
            calls.incrementAndGet();
            sleep(200);
            return new Tariff(bank);
        }

        @Cacheable(cacheNames = "fees", key = "#bank") // Явный ключ: amount не участвует.
        public String fee(String bank, long amount) {
            calls.incrementAndGet();
            return bank + " fee for " + amount;
        }

        @Cacheable(cacheNames = "limits", unless = "#result == null") // null не кладём.
        public String limit(String bank) {
            calls.incrementAndGet();
            return bank.equals("unknown") ? null : bank + " limit 1000";
        }

        @CachePut(cacheNames = "tariffs", key = "#t.bank") // Всегда вызывает метод и обновляет кеш.
        public Tariff update(Tariff t) { return t; }

        @CacheEvict(cacheNames = "tariffs", key = "#bank")
        public void evict(String bank) {}

        // Вызов через this: прокси с кешем не участвует.
        public Tariff quote(String bank) { return tariff(bank); }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableCaching // Без неё аннотации @Cacheable ничего не делают.
    @Import(TariffService.class)
    static class App {}

    static int stampede(TariffService s, boolean sync) throws Exception {
        int before = s.calls();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                pool.submit(() -> {
                    start.await();
                    return sync ? s.tariffSync("alfa") : s.tariff("sber");
                });
            }
            start.countDown(); // 10 потоков одновременно, кеш пуст.
        }
        return s.calls() - before;
    }

    public static void main(String[] args) throws Exception {
        boolean caffeine = args.length > 0 && args[0].equals("caffeine");
        var props = new java.util.HashMap<String, Object>(Map.of("spring.main.banner-mode", "off", "logging.level.root", "warn"));
        // Caffeine в classpath: Boot выбрал бы его сам. Для первого опыта просим простой кеш.
        props.put("spring.cache.type", caffeine ? "caffeine" : "simple");
        if (caffeine) {
            props.put("spring.cache.caffeine.spec", "maximumSize=1000,expireAfterWrite=1s");
        }
        var app = new SpringApplication(App.class);
        app.setDefaultProperties(props);
        try (var ctx = app.run()) {
            var s = ctx.getBean(TariffService.class);
            System.out.println("cache manager: " + ctx.getBean(CacheManager.class).getClass().getSimpleName());
            System.out.println("service class: " + s.getClass().getName().replaceAll(".*\\$TariffService", "TariffService"));

            if (caffeine) {
                s.tariff("vtb");
                s.tariff("vtb");
                System.out.println("calls after 2 reads: " + s.calls());
                Thread.sleep(1100); // TTL 1 секунда.
                s.tariff("vtb");
                System.out.println("calls after TTL:     " + s.calls());
                return;
            }

            long t = System.nanoTime();
            s.tariff("sber");
            long first = (System.nanoTime() - t) / 1_000_000;
            t = System.nanoTime();
            s.tariff("sber");
            long second = (System.nanoTime() - t) / 1_000_000;
            System.out.println("first read: " + first + " ms, second read: " + second + " ms, calls=" + s.calls());

            s.quote("tinkoff");
            s.quote("tinkoff");
            System.out.println("quote() twice via this: calls=" + s.calls());

            s.fee("sber", 100);
            System.out.println("fee(sber, 500) -> " + s.fee("sber", 500));

            s.limit("unknown");
            s.limit("unknown");
            System.out.println("null result twice: calls=" + s.calls());

            // Кеш в памяти отдаёт тот же объект: изменения вызывающего видны всем.
            Tariff mine = s.tariff("sber");
            mine.fees.add("promo -1%");
            System.out.println("another caller sees: " + s.tariff("sber"));
            System.out.println("same object: " + (mine == s.tariff("sber")));

            s.evict("sber");
            System.out.println("after evict: " + s.tariff("sber"));

            s.evict("sber"); // Кеш снова пуст перед одновременным промахом.
            System.out.println("stampede, no sync: calls=" + stampede(s, false));
            System.out.println("stampede, sync=true: calls=" + stampede(s, true));
        }
    }
}
