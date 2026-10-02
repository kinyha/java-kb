import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Future;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

// Запуск: кластер из docker-compose.yml, затем java -cp "$(cat target/classpath.txt)" ProducerDemo.java
public class ProducerDemo {
    static final String BOOTSTRAP = "localhost:19092,localhost:29092,localhost:39092";

    public static void main(String[] args) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP))) {
            for (String t : List.of("payments", "bulk")) {
                if (admin.listTopics().names().get().contains(t)) admin.deleteTopics(List.of(t)).all().get();
            }
            Thread.sleep(1000);
            admin.createTopics(List.of(new NewTopic("payments", 6, (short) 3), new NewTopic("bulk", 6, (short) 3))).all().get();
        }
        defaults();
        keys();
        batching();
        acks();
    }

    static KafkaProducer<String, String> producer(Map<String, Object> extra) {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        p.putAll(extra);
        return new KafkaProducer<>(p);
    }

    // ---------- умолчания современного продюсера ----------
    static void defaults() {
        var c = new ProducerConfig(Map.of("bootstrap.servers", BOOTSTRAP,
                "key.serializer", StringSerializer.class, "value.serializer", StringSerializer.class));
        System.out.println("== defaults: acks=" + c.getString("acks") + " enable.idempotence=" + c.getBoolean("enable.idempotence")
                + " max.in.flight=" + c.getInt("max.in.flight.requests.per.connection") + " linger.ms=" + c.getLong("linger.ms")
                + " delivery.timeout.ms=" + c.getInt("delivery.timeout.ms"));
    }

    // ---------- ключ выбирает партицию ----------
    static void keys() throws Exception {
        try (var p = producer(Map.of())) {
            Map<String, List<Integer>> byKey = new TreeMap<>();
            for (String key : List.of("p-1", "p-2", "p-3", "p-1", "p-2", "p-1")) {
                RecordMetadata m = p.send(new ProducerRecord<>("payments", key, "event")).get();
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(m.partition());
            }
            System.out.println("== key -> partition: " + byKey);

            List<Future<RecordMetadata>> fs = new ArrayList<>();
            for (int i = 0; i < 6; i++) fs.add(p.send(new ProducerRecord<>("payments", null, "no-key-" + i)));
            List<Integer> parts = new ArrayList<>();
            for (var f : fs) parts.add(f.get().partition());
            System.out.println("== null key, sent together: partitions " + parts);
        }
    }

    // ---------- батчи: linger.ms при редком потоке ----------
    static void batching() throws Exception {
        for (long linger : new long[] {0, 5, 50}) {
            try (var p = producer(Map.of("linger.ms", linger))) {
                int n = 1_000;
                for (int i = 0; i < n; i++) {
                    p.send(new ProducerRecord<>("bulk", "k-" + (i % 100), "x".repeat(200)));
                    java.util.concurrent.locks.LockSupport.parkNanos(200_000);   // ~5000 сообщений в секунду.
                }
                p.flush();
                System.out.printf("== linger.ms=%-2d %d records -> produce requests %.0f, avg batch %.0f bytes%n",
                        linger, n, metric(p, "request-total"), metric(p, "batch-size-avg"));
            }
        }
    }

    // ---------- acks: что продюсер узнаёт об отправке ----------
    static void acks() throws Exception {
        for (String acks : List.of("0", "1", "all")) {
            try (var p = producer(Map.of("acks", acks, "enable.idempotence", acks.equals("all")))) {
                RecordMetadata m = p.send(new ProducerRecord<>("payments", "p-9", "e")).get();
                System.out.printf("== acks=%-3s partition=%d offset=%d%n", acks, m.partition(), m.offset());
            }
        }
    }

    static double metric(KafkaProducer<?, ?> p, String name) {
        return p.metrics().entrySet().stream()
                .filter(e -> e.getKey().name().equals(name) && e.getKey().group().equals("producer-metrics"))
                .mapToDouble(e -> ((Number) e.getValue().metricValue()).doubleValue()).sum();
    }
}
