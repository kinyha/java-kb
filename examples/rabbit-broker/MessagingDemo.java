import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Запуск: RabbitMQ 4 на localhost:5672, затем java -cp "$(cat target/classpath.txt)" MessagingDemo.java
// Падение консьюмера моделируется закрытием канала: так брокер видит умерший процесс.
public class MessagingDemo {
    public static void main(String[] args) throws Exception {
        try (Connection conn = new ConnectionFactory().newConnection()) {
            pointToPointVsPubSub(conn);
            atMostOnce(conn);
            atLeastOnce(conn);
        }
    }

    // ---------- очередь против публикации-подписки ----------
    static void pointToPointVsPubSub(Connection conn) throws Exception {
        try (Channel ch = conn.createChannel()) {
            ch.queueDelete("receipts");
            ch.queueDeclare("receipts", true, false, false, null);
            List<String> w1 = java.util.Collections.synchronizedList(new ArrayList<>());
            List<String> w2 = java.util.Collections.synchronizedList(new ArrayList<>());
            Channel c1 = conn.createChannel(), c2 = conn.createChannel();     // Два воркера подписаны на одну очередь.
            c1.basicQos(1); c2.basicQos(1);
            c1.basicConsume("receipts", true, (t, d) -> w1.add(new String(d.getBody())), t -> {});
            c2.basicConsume("receipts", true, (t, d) -> w2.add(new String(d.getBody())), t -> {});
            for (int i = 1; i <= 4; i++) ch.basicPublish("", "receipts", null, ("p-" + i).getBytes());
            Thread.sleep(300);
            c1.close(); c2.close();
            System.out.println("== queue (competing consumers): worker1=" + w1 + " worker2=" + w2);

            ch.exchangeDelete("payment-events");
            ch.exchangeDeclare("payment-events", "fanout", true);
            for (String q : List.of("antifraud", "analytics")) {
                ch.queueDelete(q);
                ch.queueDeclare(q, true, false, false, null);
                ch.queueBind(q, "payment-events", "");
            }
            for (int i = 1; i <= 2; i++) ch.basicPublish("payment-events", "", null, ("p-" + i).getBytes());
            System.out.println("== pub/sub (fanout): antifraud=" + drain(ch, "antifraud") + " analytics=" + drain(ch, "analytics"));
        }
    }

    // ---------- at-most-once: подтверждение до обработки ----------
    static void atMostOnce(Connection conn) throws Exception {
        reset(conn, "charges");
        Channel ch = conn.createChannel();
        List<String> processed = new ArrayList<>();
        List<String> received = new ArrayList<>();
        for (int i = 0; i < 3; i++) received.add(body(ch.basicGet("charges", true)));   // autoAck: брокер удалил сразу.
        processed.add(received.get(0));                                              // Обработал одно и упал.
        ch.close();
        try (Channel check = conn.createChannel()) {
            System.out.println("== at-most-once (autoAck): received " + received + ", processed " + processed
                    + ", left in queue " + check.messageCount("charges"));
        }
    }

    // ---------- at-least-once: подтверждение после обработки ----------
    static void atLeastOnce(Connection conn) throws Exception {
        reset(conn, "charges");
        Set<String> done = new HashSet<>();                 // Таблица обработанных у консьюмера.
        List<String> log = new ArrayList<>();

        Channel ch = conn.createChannel();
        GetResponse r1 = ch.basicGet("charges", false);
        handle(r1, done, log);
        ch.basicAck(r1.getEnvelope().getDeliveryTag(), false);
        GetResponse r2 = ch.basicGet("charges", false);
        handle(r2, done, log);                              // Деньги списаны...
        ch.close();                                         // ...и процесс упал до ack.

        Channel ch2 = conn.createChannel();
        for (GetResponse r; (r = ch2.basicGet("charges", false)) != null; ) {
            handle(r, done, log);
            ch2.basicAck(r.getEnvelope().getDeliveryTag(), false);
        }
        ch2.close();
        System.out.println("== at-least-once (ack after work) + idempotent consumer:");
        log.forEach(l -> System.out.println("  " + l));
    }

    static void handle(GetResponse r, Set<String> done, List<String> log) {
        String id = r.getProps().getMessageId();
        boolean fresh = done.add(id);                       // В проде — insert в той же транзакции, что и работа.
        log.add(body(r) + " id=" + id + " redelivered=" + r.getEnvelope().isRedeliver() + " -> " + (fresh ? "charged" : "duplicate, skipped"));
    }

    static void reset(Connection conn, String q) throws Exception {
        try (Channel ch = conn.createChannel()) {
            ch.queueDelete(q);
            ch.queueDeclare(q, true, false, false, Map.of("x-queue-type", "quorum"));
            for (int i = 1; i <= 3; i++) {
                var props = new AMQP.BasicProperties.Builder().messageId("evt-" + i).deliveryMode(2).build();
                ch.basicPublish("", q, props, ("charge p-" + i).getBytes());
            }
        }
    }

    static List<String> drain(Channel ch, String q) throws Exception {
        List<String> out = new ArrayList<>();
        for (GetResponse r; (r = ch.basicGet(q, true)) != null; ) out.add(body(r));
        return out;
    }

    static String body(GetResponse r) { return new String(r.getBody(), StandardCharsets.UTF_8); }
}
