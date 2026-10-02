package kb.rabbit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Нужен RabbitMQ на localhost:5672, например:
//   docker run -d --name kb-rabbit -p 5672:5672 rabbitmq:4-management
// Запуск: ./run.sh RabbitDemo routing   — exchange, binding, возврат и подтверждение
//         ./run.sh RabbitDemo requeue   — исключение без настройки: classic и quorum
//         ./run.sh RabbitDemo dlx       — повторы в приложении и dead letter exchange
public class RabbitDemo {

    static final long T0 = System.currentTimeMillis();
    static long now() { return System.currentTimeMillis() - T0; }

    // ---------- routing ----------
    @Configuration
    static class Routing {
        final Map<String, List<String>> got = new ConcurrentHashMap<>();
        final CountDownLatch latch = new CountDownLatch(4);

        @Bean
        Declarables topology() {
            var ex = new TopicExchange("payments");
            var ledger = QueueBuilder.durable("ledger").build();
            var alerts = QueueBuilder.durable("alerts").build();
            var audit = QueueBuilder.durable("audit").build();
            return new Declarables(ex, ledger, alerts, audit,
                    BindingBuilder.bind(ledger).to(ex).with("payment.completed"),
                    BindingBuilder.bind(alerts).to(ex).with("payment.failed"),
                    BindingBuilder.bind(audit).to(ex).with("payment.*")); // * — ровно одно слово.
        }

        @RabbitListener(queues = {"ledger", "alerts", "audit"})
        void on(String body, Message m) {
            got.computeIfAbsent(m.getMessageProperties().getConsumerQueue(), q -> new CopyOnWriteArrayList<>()).add(body);
            latch.countDown();
        }
    }

    static void routing() throws Exception {
        try (var ctx = start(Routing.class, Map.of(
                "spring.rabbitmq.publisher-confirm-type", "correlated", // Брокер подтверждает приём.
                "spring.rabbitmq.publisher-returns", "true",            // И возвращает неразроутенное.
                "spring.rabbitmq.template.mandatory", "true"))) {
            var t = ctx.getBean(RabbitTemplate.class);
            t.setReturnsCallback(r -> System.out.println("returned " + r.getRoutingKey() + ": " + r.getReplyText()));
            for (String key : List.of("payment.completed", "payment.failed", "refund.completed")) {
                var cd = new CorrelationData(key);
                t.convertAndSend("payments", key, key, cd);
                var confirm = cd.getFuture().get(5, TimeUnit.SECONDS);
                System.out.println("sent " + key + ": confirm ack=" + confirm.ack());
            }
            var r = ctx.getBean(Routing.class);
            r.latch.await(5, TimeUnit.SECONDS);
            System.out.println("ledger=" + r.got.get("ledger") + ", alerts=" + r.got.get("alerts") + ", audit=" + r.got.get("audit"));
        }
    }

    // ---------- requeue ----------
    @Configuration
    static class Requeue {
        final Map<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        volatile Object lastDeliveryCount;

        @Bean
        Declarables queues() {
            return new Declarables(
                    QueueBuilder.durable("charges-classic").build(),
                    QueueBuilder.durable("charges-quorum").quorum().build()); // x-queue-type=quorum
        }

        @RabbitListener(queues = {"charges-classic", "charges-quorum"})
        void on(String body, Message m) {
            if (m.getMessageProperties().getConsumerQueue().equals("charges-quorum")) {
                lastDeliveryCount = m.getMessageProperties().getHeader("x-delivery-count");
            }
            deliveries.computeIfAbsent(m.getMessageProperties().getConsumerQueue(), q -> new AtomicInteger()).incrementAndGet();
            throw new IllegalStateException("bank declined"); // Каждая доставка падает.
        }
    }

    static void requeue() throws Exception {
        try (var ctx = start(Requeue.class, Map.of())) {
            var t = ctx.getBean(RabbitTemplate.class);
            t.convertAndSend("", "charges-classic", "bad");
            t.convertAndSend("", "charges-quorum", "bad");
            Thread.sleep(3000);
            var d = ctx.getBean(Requeue.class).deliveries;
            System.out.println("deliveries in 3 s: classic=" + d.get("charges-classic") + ", quorum=" + d.get("charges-quorum"));
            var admin = ctx.getBean(RabbitAdmin.class);
            System.out.println("quorum x-delivery-count on last delivery: " + ctx.getBean(Requeue.class).lastDeliveryCount);
            System.out.println("left in queue: classic=" + count(admin, "charges-classic") + ", quorum=" + count(admin, "charges-quorum"));
            admin.deleteQueue("charges-classic"); // Иначе сообщение останется крутиться в брокере.
            admin.deleteQueue("charges-quorum");
        }
    }

    // ---------- dlx ----------
    @Configuration
    static class Dlx {
        final AtomicInteger attempts = new AtomicInteger();
        final CountDownLatch dead = new CountDownLatch(1);

        @Bean
        Declarables refunds() {
            var dlx = new TopicExchange("dlx");
            var dlq = QueueBuilder.durable("refunds.dlq").build();
            var refunds = QueueBuilder.durable("refunds")
                    .deadLetterExchange("dlx")              // Куда брокер переложит отвергнутое.
                    .deadLetterRoutingKey("refunds.dead")
                    .build();
            return new Declarables(dlx, dlq, refunds, BindingBuilder.bind(dlq).to(dlx).with("refunds.dead"));
        }

        @RabbitListener(queues = "refunds")
        void on(String body) {
            System.out.println(now() + " ms: attempt " + attempts.incrementAndGet() + " for " + body);
            throw new IllegalStateException("bank declined");
        }

        @RabbitListener(queues = "refunds.dlq")
        void dead(String body, Message m) {
            List<Map<String, ?>> xDeath = m.getMessageProperties().getXDeathHeader();
            System.out.println("DLQ got " + body + ", x-death reason=" + xDeath.get(0).get("reason")
                    + ", queue=" + xDeath.get(0).get("queue"));
            dead.countDown();
        }
    }

    static void dlx() throws Exception {
        try (var ctx = start(Dlx.class, Map.of(
                "spring.rabbitmq.listener.simple.retry.enabled", "true",       // Повторы внутри приложения.
                "spring.rabbitmq.listener.simple.retry.max-retries", "2", // Boot 4: повторы сверх первой попытки.
                "spring.rabbitmq.listener.simple.retry.initial-interval", "300ms",
                "spring.rabbitmq.listener.simple.retry.multiplier", "2"))) {
            ctx.getBean(RabbitTemplate.class).convertAndSend("", "refunds", "refund-7");
            ctx.getBean(Dlx.class).dead.await(10, TimeUnit.SECONDS);
            var admin = ctx.getBean(RabbitAdmin.class);
            admin.deleteQueue("refunds");
            admin.deleteQueue("refunds.dlq");
        }
    }

    // ---------- обвязка ----------
    static long count(RabbitAdmin admin, String queue) {
        var info = admin.getQueueInfo(queue);
        return info == null ? -1 : info.getMessageCount();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {}

    static ConfigurableApplicationContext start(Class<?> config, Map<String, Object> extra) {
        var app = new SpringApplication(App.class, config);
        var props = new java.util.HashMap<String, Object>(Map.of(
                "spring.main.banner-mode", "off", "logging.level.root", "off"));
        props.putAll(extra);
        app.setDefaultProperties(props);
        return app.run();
    }

    public static void main(String[] args) throws Exception {
        switch (args.length == 0 ? "routing" : args[0]) {
            case "routing" -> routing();
            case "requeue" -> requeue();
            case "dlx" -> dlx();
            default -> System.out.println("routing | requeue | dlx");
        }
    }
}
