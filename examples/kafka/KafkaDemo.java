package kb.kafka;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.util.backoff.FixedBackOff;

// Запуск: ./run.sh KafkaDemo basics   — ключ и партиция, concurrency
//         ./run.sh KafkaDemo errors   — обработчик ошибок по умолчанию
//         ./run.sh KafkaDemo dlt      — повторы с паузой и dead letter topic
public class KafkaDemo {

    static final long T0 = System.currentTimeMillis();
    static long now() { return System.currentTimeMillis() - T0; }

    // ---------- basics ----------
    @Configuration
    static class Basics {
        final Map<Integer, String> threadByPartition = new ConcurrentHashMap<>();
        final CountDownLatch received = new CountDownLatch(6);

        @KafkaListener(id = "ledger", topics = "payments", concurrency = "4") // 4 потребителя на 3 партиции.
        void onPayment(ConsumerRecord<String, String> r) {
            threadByPartition.put(r.partition(), Thread.currentThread().getName().replaceAll("-C-1$", ""));
            received.countDown();
        }
    }

    static void basics(String brokers) throws Exception {
        try (var ctx = start(brokers, Basics.class)) {
            KafkaTemplate<String, String> template = ctx.getBean(KafkaTemplate.class);
            var group = (ConcurrentMessageListenerContainer<?, ?>) ctx.getBean(KafkaListenerEndpointRegistry.class)
                    .getListenerContainer("ledger");
            // Ждём, пока ребалансировка раздаст 3 партиции разным потребителям.
            for (int i = 0; i < 150 && group.getContainers().stream()
                    .filter(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()).count() < 3; i++) {
                Thread.sleep(100);
            }
            for (String key : List.of("acc-1", "acc-2", "acc-3", "acc-1", "acc-2", "acc-3")) {
                // send асинхронный: get() только чтобы напечатать, куда легла запись.
                var meta = template.send("payments", key, "pay " + key).get().getRecordMetadata();
                System.out.println("sent key=" + key + " -> partition " + meta.partition() + ", offset " + meta.offset());
            }
            ctx.getBean(Basics.class).received.await(10, TimeUnit.SECONDS);
            System.out.println("consumed: " + new TreeMap<>(ctx.getBean(Basics.class).threadByPartition));
            var container = (ConcurrentMessageListenerContainer<?, ?>) ctx.getBean(KafkaListenerEndpointRegistry.class)
                    .getListenerContainer("ledger");
            container.getContainers().forEach(c -> System.out.println(
                    c.getListenerId() + " assigned " + c.getAssignedPartitions().stream().map(p -> p.partition()).sorted().toList()));
        }
    }

    // ---------- errors: обработчик по умолчанию ----------
    @Configuration
    static class Errors {
        final AtomicInteger attempts = new AtomicInteger();
        final List<String> log = new CopyOnWriteArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);

        @KafkaListener(id = "charger", topics = "charges")
        void onCharge(String value) {
            if (value.equals("bad")) {
                attempts.incrementAndGet();
                throw new IllegalStateException("bank declined"); // Ошибка на каждой попытке.
            }
            log.add(now() + " ms: processed " + value);
            if (value.equals("ok-2")) done.countDown();
        }
    }

    static void errors(String brokers) throws Exception {
        try (var ctx = start(brokers, Errors.class)) {
            KafkaTemplate<String, String> template = ctx.getBean(KafkaTemplate.class);
            waitForAssignment(ctx, "charger");
            for (String v : List.of("ok-1", "bad", "ok-2")) template.send("charges", "acc-1", v); // Одна партиция.
            var errors = ctx.getBean(Errors.class);
            errors.done.await(30, TimeUnit.SECONDS);
            errors.log.forEach(System.out::println);
            System.out.println("attempts for bad: " + errors.attempts.get());
        }
    }

    // ---------- dlt: свой обработчик ----------
    @Configuration
    static class Dlt {
        final AtomicInteger attempts = new AtomicInteger();
        final CountDownLatch dead = new CountDownLatch(1);

        @Bean // Boot подключит этот бин к фабрике контейнеров.
        CommonErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
            var recoverer = new DeadLetterPublishingRecoverer(template); // В топик <имя>-dlt, та же партиция.
            var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(200, 2)); // 1 + 2 повтора, пауза 200 мс.
            handler.addNotRetryableExceptions(IllegalArgumentException.class); // Сразу в DLT, без повторов.
            return handler;
        }

        @KafkaListener(id = "refunds", topics = "refunds")
        void onRefund(String value) {
            attempts.incrementAndGet();
            System.out.println(now() + " ms: attempt for " + value);
            if (value.equals("bad")) throw new IllegalStateException("bank declined");
            if (value.equals("broken-json")) throw new IllegalArgumentException("cannot parse");
        }

        @KafkaListener(id = "refunds-dlt", topics = "refunds-dlt")
        void onDead(String value, @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String error,
                    @Header(KafkaHeaders.DLT_ORIGINAL_OFFSET) long offset) {
            System.out.println("DLT got " + value + " (offset " + offset + "): " + error);
            if (value.equals("bad")) dead.countDown();
        }
    }

    static void dlt(String brokers) throws Exception {
        try (var ctx = start(brokers, Dlt.class)) {
            KafkaTemplate<String, String> template = ctx.getBean(KafkaTemplate.class);
            waitForAssignment(ctx, "refunds");
            waitForAssignment(ctx, "refunds-dlt");
            template.send("refunds", "acc-1", "broken-json");
            template.send("refunds", "acc-1", "bad");
            ctx.getBean(Dlt.class).dead.await(30, TimeUnit.SECONDS);
            Thread.sleep(300);
        }
    }

    // ---------- обвязка ----------
    static void waitForAssignment(ConfigurableApplicationContext ctx, String id) throws Exception {
        var c = ctx.getBean(KafkaListenerEndpointRegistry.class).getListenerContainer(id);
        for (int i = 0; i < 100 && (c.getAssignedPartitions() == null || c.getAssignedPartitions().isEmpty()); i++) {
            Thread.sleep(100);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {}

    static ConfigurableApplicationContext start(String brokers, Class<?> config) {
        var app = new SpringApplication(App.class, config);
        app.setDefaultProperties(Map.of(
                "spring.kafka.bootstrap-servers", brokers,
                "spring.kafka.consumer.group-id", "payments-service",
                "spring.kafka.consumer.auto-offset-reset", "earliest",
                "spring.main.banner-mode", "off",
                "logging.level.root", "error",
                "logging.level.org.springframework.kafka.listener", "error"));
        return app.run();
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "basics" : args[0];
        // Один брокер KRaft в этой JVM; у каждого топика 3 партиции.
        var broker = new EmbeddedKafkaKraftBroker(1, 3, "payments", "charges", "refunds", "refunds-dlt");
        broker.afterPropertiesSet();
        try {
            switch (mode) {
                case "basics" -> basics(broker.getBrokersAsString());
                case "errors" -> errors(broker.getBrokersAsString());
                case "dlt" -> dlt(broker.getBrokersAsString());
                default -> System.out.println("basics | errors | dlt");
            }
        } finally {
            broker.destroy();
        }
    }
}
