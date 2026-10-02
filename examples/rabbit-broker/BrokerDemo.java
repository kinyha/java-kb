import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Запуск: RabbitMQ 4 на localhost:5672 (docker run -d --name kb-rabbit -p 5672:5672 rabbitmq:4-management),
// затем java -cp "$(cat target/classpath.txt)" BrokerDemo.java
public class BrokerDemo {
    public static void main(String[] args) throws Exception {
        ConnectionFactory f = new ConnectionFactory();
        try (Connection conn = f.newConnection()) {
            prefetch(conn, 0);      // 0 — без ограничения.
            prefetch(conn, 1);
            requeueOrder(conn, "classic");
            requeueOrder(conn, "quorum");
            poisonMessage(conn);
            singleActiveConsumer(conn);
        }
    }

    // ---------- prefetch: сколько неподтверждённых сообщений брокер отдаёт консьюмеру ----------
    static void prefetch(Connection conn, int prefetch) throws Exception {
        String q = "tasks-" + prefetch;
        try (Channel admin = conn.createChannel()) {
            admin.queueDelete(q);
            admin.queueDeclare(q, true, false, false, Map.of("x-queue-type", "quorum"));
        }
        CountDownLatch done = new CountDownLatch(20);
        AtomicInteger fast = new AtomicInteger(), slow = new AtomicInteger();
        Channel a = worker(conn, q, prefetch, 5, fast, done);      // 5 мс на задачу.
        Channel b = worker(conn, q, prefetch, 300, slow, done);    // 300 мс на задачу.
        long t = System.nanoTime();
        try (Channel pub = conn.createChannel()) {                 // Оба уже подписаны.
            for (int i = 1; i <= 20; i++) pub.basicPublish("", q, null, ("task-" + i).getBytes());
        }
        done.await(30, TimeUnit.SECONDS);
        System.out.printf("== prefetch %s: fast=%d slow=%d, all done in ~%d ms%n",
                prefetch == 0 ? "unlimited" : prefetch, fast.get(), slow.get(), Math.round((System.nanoTime() - t) / 1e8) * 100);
        a.close(); b.close();
    }

    static Channel worker(Connection conn, String q, int prefetch, long workMs, AtomicInteger count, CountDownLatch done) throws Exception {
        Channel ch = conn.createChannel();
        if (prefetch > 0) ch.basicQos(prefetch);
        ch.basicConsume(q, false, new DefaultConsumer(ch) {
            @Override public void handleDelivery(String tag, Envelope env, AMQP.BasicProperties p, byte[] body) throws java.io.IOException {
                sleep(workMs);
                getChannel().basicAck(env.getDeliveryTag(), false);
                count.incrementAndGet();
                done.countDown();
            }
        });
        return ch;
    }

    // ---------- порядок после requeue ----------
    static void requeueOrder(Connection conn, String type) throws Exception {
        String q = "order-" + type;
        try (Channel admin = conn.createChannel()) {
            admin.queueDelete(q);
            admin.queueDeclare(q, true, false, false, Map.of("x-queue-type", type));
            for (String e : List.of("p-1:AUTHORIZED", "p-1:CAPTURED", "p-1:REFUNDED")) admin.basicPublish("", q, null, e.getBytes());
        }
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(3);
        Channel ch = conn.createChannel();
        ch.basicQos(10);
        AtomicInteger failures = new AtomicInteger();
        ch.basicConsume(q, false, new DefaultConsumer(ch) {
            @Override public void handleDelivery(String tag, Envelope env, AMQP.BasicProperties p, byte[] body) throws java.io.IOException {
                String e = new String(body, StandardCharsets.UTF_8);
                if (e.equals("p-1:AUTHORIZED") && failures.getAndIncrement() == 0) {  // Первая обработка упала.
                    getChannel().basicNack(env.getDeliveryTag(), false, true);           // requeue=true
                    return;
                }
                seen.add(e);
                getChannel().basicAck(env.getDeliveryTag(), false);
                done.countDown();
            }
        });
        done.await(10, TimeUnit.SECONDS);
        System.out.println("== requeue order, " + type + " queue: " + seen);
        ch.close();
    }

    // ---------- ядовитое сообщение в quorum-очереди ----------
    static void poisonMessage(Connection conn) throws Exception {
        try (Channel admin = conn.createChannel()) {
            admin.queueDelete("payments");
            admin.queueDelete("payments.dlq");
            admin.exchangeDelete("dlx");
            admin.exchangeDeclare("dlx", "fanout", true);
            admin.queueDeclare("payments.dlq", true, false, false, null);
            admin.queueBind("payments.dlq", "dlx", "");
            admin.queueDeclare("payments", true, false, false, Map.of(
                    "x-queue-type", "quorum",
                    "x-dead-letter-exchange", "dlx"));                 // delivery-limit не задан: по умолчанию.
            admin.basicPublish("", "payments", null, "{\"amount\":\"oops\"}".getBytes());
        }
        // 1) Листенер ловит исключение и делает nack с requeue=true.
        AtomicInteger deliveries = new AtomicInteger();
        List<Object> counts = Collections.synchronizedList(new ArrayList<>());
        Channel ch = conn.createChannel();
        ch.basicConsume("payments", false, new DefaultConsumer(ch) {
            @Override public void handleDelivery(String tag, Envelope env, AMQP.BasicProperties p, byte[] body) throws java.io.IOException {
                if (deliveries.incrementAndGet() <= 3) counts.add(deliveryCount(p));
                getChannel().basicNack(env.getDeliveryTag(), false, true);
            }
        });
        sleep(1000);
        ch.close();
        System.out.println("== poison + nack(requeue): " + deliveries.get() + " deliveries in 1 s, x-delivery-count " + counts);

        // 2) Консьюмер падает, не подтвердив: канал закрывается вместе с доставкой.
        int crashes = 0;
        while (true) {
            Channel c = conn.createChannel();
            var got = c.basicGet("payments", false);
            if (got == null) { c.close(); break; }
            crashes++;
            c.close();                                                    // Без ack: как упавший процесс.
        }
        try (Channel admin = conn.createChannel()) {
            var dead = admin.basicGet("payments.dlq", true);
            var death = (Map<?, ?>) ((List<?>) dead.getProps().getHeaders().get("x-death")).get(0);
            System.out.println("== poison + consumer crash: " + crashes + " deliveries, then DLQ reason=" + death.get("reason"));
        }
    }

    static Object deliveryCount(AMQP.BasicProperties p) {
        return p.getHeaders() == null ? null : p.getHeaders().get("x-delivery-count");
    }

    // ---------- single active consumer ----------
    static void singleActiveConsumer(Connection conn) throws Exception {
        String q = "ledger";
        try (Channel admin = conn.createChannel()) {
            admin.queueDelete(q);
            admin.queueDeclare(q, true, false, false, Map.of("x-queue-type", "quorum", "x-single-active-consumer", true));
        }
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        Channel a = conn.createChannel(), b = conn.createChannel();
        String tagA = a.basicConsume(q, true, (tag, d) -> log.add("A:" + new String(d.getBody())), tag -> {});
        b.basicConsume(q, true, (tag, d) -> log.add("B:" + new String(d.getBody())), tag -> {});
        try (Channel pub = conn.createChannel()) {
            for (int i = 1; i <= 3; i++) pub.basicPublish("", q, null, ("e" + i).getBytes());
            sleep(300);
            a.basicCancel(tagA);                                                // A ушёл: деплой, падение.
            sleep(300);
            for (int i = 4; i <= 6; i++) pub.basicPublish("", q, null, ("e" + i).getBytes());
            sleep(300);
        }
        System.out.println("== single active consumer: " + log);
        a.close(); b.close();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
