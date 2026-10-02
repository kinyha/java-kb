import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

// Запуск: кластер из docker-compose.yml, затем java -cp "$(cat target/classpath.txt)" ConsumerDemo.java
public class ConsumerDemo {
    static final String BOOTSTRAP = "localhost:19092,localhost:29092,localhost:39092";
    static final String TOPIC = "orders";

    public static void main(String[] args) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP))) {
            if (admin.listTopics().names().get().contains(TOPIC)) admin.deleteTopics(List.of(TOPIC)).all().get();
            Thread.sleep(1000);
            admin.createTopics(List.of(new NewTopic(TOPIC, 6, (short) 3))).all().get();
        }
        produce(60);
        assignment(3);
        assignment(7);
        lagAndRestart();
        slowProcessing();
    }

    static KafkaConsumer<String, String> consumer(String group, Map<String, Object> extra) {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("group.id", group);
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("auto.offset.reset", "earliest");
        p.putAll(extra);
        return new KafkaConsumer<>(p);
    }

    static void produce(int n) throws Exception {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        try (var producer = new KafkaProducer<String, String>(p)) {
            for (int i = 0; i < n; i++) producer.send(new ProducerRecord<>(TOPIC, "order-" + i, "created"));
        }
    }

    // ---------- распределение партиций в группе ----------
    static void assignment(int consumers) throws Exception {
        String group = "assign-" + consumers;
        Map<String, Collection<TopicPartition>> owned = new ConcurrentHashMap<>();
        AtomicBoolean stop = new AtomicBoolean();
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= consumers; i++) {
            String name = "c" + i;
            threads.add(Thread.ofPlatform().start(() -> {
                try (var c = consumer(group, Map.of())) {
                    c.subscribe(List.of(TOPIC));
                    while (!stop.get()) {
                        c.poll(Duration.ofMillis(200));
                        owned.put(name, new ArrayList<>(c.assignment()));
                    }
                }
            }));
        }
        Thread.sleep(8000);
        Map<String, List<Integer>> view = new TreeMap<>();                // Снимок до остановки.
        owned.forEach((k, v) -> view.put(k, v.stream().map(TopicPartition::partition).sorted().toList()));
        stop.set(true);
        for (Thread t : threads) t.join();
        System.out.println("== " + consumers + " consumers, 6 partitions: " + view);
    }

    // ---------- коммит смещения, lag и перезапуск ----------
    static void lagAndRestart() throws Exception {
        String group = "billing";
        int processed = 0;
        try (var c = consumer(group, Map.of("enable.auto.commit", false, "max.poll.records", 10))) {
            c.subscribe(List.of(TOPIC));
            while (processed < 25) {
                var records = c.poll(Duration.ofMillis(500));
                processed += records.count();
                if (processed <= 20) c.commitSync();          // Коммитим только первые пачки, потом «падаем».
            }
        }
        long committed, end;
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP))) {
            var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get();
            committed = offsets.values().stream().mapToLong(o -> o.offset()).sum();
            var ends = admin.listOffsets(offsets.keySet().stream().collect(java.util.stream.Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest())))
                    .all().get();
            end = ends.values().stream().mapToLong(o -> o.offset()).sum();
        }
        System.out.println("== processed " + processed + ", committed " + committed + ", lag " + (end - committed) + " of " + end);
        int again = 0;
        try (var c = consumer(group, Map.of("enable.auto.commit", false))) {
            c.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline && again < end - committed) again += c.poll(Duration.ofMillis(500)).count();
        }
        System.out.println("== after restart the group reads " + again + " records: " + (processed - committed) + " of them already processed");
    }

    // ---------- медленная обработка: потребитель вылетает из группы ----------
    static void slowProcessing() throws Exception {
        try (var c = consumer("slow", Map.of("enable.auto.commit", false, "max.poll.records", 5, "max.poll.interval.ms", 3000))) {
            List<String> events = new ArrayList<>();
            c.subscribe(List.of(TOPIC), new ConsumerRebalanceListener() {
                public void onPartitionsRevoked(Collection<TopicPartition> p) { if (!p.isEmpty()) events.add("revoked " + p.size()); }
                public void onPartitionsAssigned(Collection<TopicPartition> p) { if (!p.isEmpty()) events.add("assigned " + p.size()); }
                public void onPartitionsLost(Collection<TopicPartition> p) { events.add("lost " + p.size()); }
            });
            var records = c.poll(Duration.ofSeconds(5));
            while (records.isEmpty()) records = c.poll(Duration.ofSeconds(1));
            long t = System.nanoTime();
            Thread.sleep(5000);                                // Обработка пачки дольше max.poll.interval.ms.
            try {
                c.commitSync();
                System.out.println("== slow batch committed");
            } catch (CommitFailedException e) {
                System.out.println("== slow batch " + records.count() + " records, " + Math.round((System.nanoTime() - t) / 1e9)
                        + " s > max.poll.interval.ms=3000 -> " + e.getClass().getSimpleName());
            } catch (Exception e) {
                System.out.println("== slow batch: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            c.poll(Duration.ofSeconds(3));
            System.out.println("   rebalance callbacks: " + events);
        }
    }
}
