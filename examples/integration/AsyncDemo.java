package kb.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskDecorator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

// Запуск: ./run.sh AsyncDemo async   — @Async: поток, пул, ошибки, MDC
//         ./run.sh AsyncDemo sched   — @Scheduled: один поток планировщика
//         ./run.sh AsyncDemo lock    — ShedLock: два экземпляра, общая база
public class AsyncDemo {

    // ---------- @Async ----------
    @Service
    static class Notifier {
        @Async // void: исключение некому вернуть.
        public void sendSms(String paymentId) {
            System.out.println("sendSms " + paymentId + " on " + Thread.currentThread().getName()
                    + ", requestId=" + MDC.get("requestId"));
            if (paymentId.equals("bad")) throw new IllegalStateException("SMS gateway down");
        }

        @Async // Future: исключение и результат вернутся вызывающему.
        public CompletableFuture<String> receipt(String paymentId) {
            if (paymentId.equals("bad")) throw new IllegalStateException("PDF render failed");
            return CompletableFuture.completedFuture("receipt-" + paymentId);
        }

        @Async
        public void slow() { sleep(300); }

        // Вызов через this: аннотация на sendSms не работает.
        public void notifyAll(String paymentId) { sendSms(paymentId); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableAsync // Без неё @Async выполняется в потоке вызывающего.
    @Import(Notifier.class)
    static class AsyncApp {}

    // TaskDecorator-бин Boot сам подключит к applicationTaskExecutor.
    @Configuration
    static class MdcDecorator {
        @Bean
        TaskDecorator mdcCopy() {
            return task -> {
                Map<String, String> mdc = MDC.getCopyOfContextMap(); // Поток вызывающего.
                return () -> {
                    if (mdc != null) MDC.setContextMap(mdc); // Поток пула.
                    try { task.run(); } finally { MDC.clear(); }
                };
            };
        }
    }

    static void asyncPart() throws Exception {
        try (var ctx = start(Map.of(), AsyncApp.class)) {
            var n = ctx.getBean(Notifier.class);
            MDC.put("requestId", "req-42");
            System.out.println("caller thread: " + Thread.currentThread().getName());
            n.sendSms("p-1");
            n.notifyAll("p-2");
            sleep(100);
            n.sendSms("bad");
            sleep(100);
            try {
                n.receipt("bad").get();
            } catch (java.util.concurrent.ExecutionException e) {
                System.out.println("receipt failed: " + e.getCause().getMessage());
            }

            var pool = ctx.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
            for (int i = 0; i < 20; i++) n.slow();
            sleep(100);
            System.out.println("pool: core=" + pool.getCorePoolSize() + ", max=" + pool.getMaxPoolSize()
                    + ", threads=" + pool.getPoolSize() + ", active=" + pool.getActiveCount()
                    + ", queued=" + pool.getThreadPoolExecutor().getQueue().size());
        }
        System.out.println("-- with TaskDecorator");
        try (var ctx = start(Map.of(), AsyncApp.class, MdcDecorator.class)) {
            MDC.put("requestId", "req-42");
            ctx.getBean(Notifier.class).sendSms("p-3");
            sleep(100);
        }
        MDC.clear();
    }

    // ---------- @Scheduled ----------
    @Service
    static class Jobs {
        final List<Long> heartbeats = Collections.synchronizedList(new ArrayList<>());
        final long start = System.currentTimeMillis();

        @Scheduled(fixedRate = 100) // Каждые 100 мс.
        public void heartbeat() { heartbeats.add(System.currentTimeMillis() - start); }

        @Scheduled(fixedRate = 1000) // Отчёт, который выполняется 600 мс.
        public void report() {
            System.out.println("report on " + Thread.currentThread().getName());
            sleep(600);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableScheduling
    @Import(Jobs.class)
    static class SchedApp {}

    static void schedPart() throws Exception {
        for (String size : List.of("1", "2")) {
            try (var ctx = start(Map.of("spring.task.scheduling.pool.size", size), SchedApp.class)) {
                sleep(1200);
                var beats = new ArrayList<>(ctx.getBean(Jobs.class).heartbeats);
                // Моменты запуска heartbeat от старта, мс.
                System.out.println("pool.size=" + size + ": heartbeats=" + beats.size() + " at " + beats);
            }
        }
    }

    // ---------- ShedLock ----------
    @Service
    static class Reconciliation {
        final AtomicInteger runs = new AtomicInteger();

        @Scheduled(fixedRate = 100)
        @SchedulerLock(name = "reconcile", lockAtLeastFor = "PT0.4S") // Держать замок минимум 400 мс.
        public void reconcile() { runs.incrementAndGet(); }

        public int runs() { return runs.get(); } // Бин в прокси ShedLock: читаем через метод.
    }

    @Service
    static class NoLockReconciliation {
        final AtomicInteger runs = new AtomicInteger();

        @Scheduled(fixedRate = 100) // Без замка: каждый экземпляр сам по себе.
        public void reconcile() { runs.incrementAndGet(); }
    }

    @Configuration
    @EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
    static class LockConfig {
        @Bean
        LockProvider lockProvider(DataSource ds) {
            new JdbcTemplate(ds).execute("CREATE TABLE IF NOT EXISTS shedlock(name VARCHAR(64) PRIMARY KEY, "
                    + "lock_until TIMESTAMP(3) NOT NULL, locked_at TIMESTAMP(3) NOT NULL, locked_by VARCHAR(255) NOT NULL)");
            return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                    .withJdbcTemplate(new JdbcTemplate(ds)).usingDbTime().build());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableScheduling
    @Import({Reconciliation.class, NoLockReconciliation.class, LockConfig.class})
    static class LockApp {}

    static void lockPart() throws Exception {
        // Два «экземпляра» сервиса в одной JVM, общая база H2 в памяти.
        var shared = Map.<String, Object>of("spring.datasource.url", "jdbc:h2:mem:shared;DB_CLOSE_DELAY=-1",
                "spring.datasource.generate-unique-name", "false");
        try (var a = start(shared, LockApp.class); var b = start(shared, LockApp.class)) {
            sleep(1500);
            int la = a.getBean(Reconciliation.class).runs(), lb = b.getBean(Reconciliation.class).runs();
            int na = a.getBean(NoLockReconciliation.class).runs.get(), nb = b.getBean(NoLockReconciliation.class).runs.get();
            System.out.println("without lock: A=" + na + ", B=" + nb + ", total=" + (na + nb));
            System.out.println("with ShedLock: A=" + la + ", B=" + lb + ", total=" + (la + lb));
        }
    }

    static ConfigurableApplicationContext start(Map<String, Object> extra, Class<?>... sources) {
        var app = new SpringApplication(sources);
        var props = new java.util.HashMap<String, Object>(Map.of("spring.main.banner-mode", "off", "logging.level.root", "error"));
        props.putAll(extra);
        app.setDefaultProperties(props);
        return app.run();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) throws Exception {
        switch (args.length == 0 ? "async" : args[0]) {
            case "async" -> asyncPart();
            case "sched" -> schedPart();
            case "lock" -> lockPart();
            default -> System.out.println("async | sched | lock");
        }
    }
}
