import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

// Запуск: кластер из docker-compose.yml, затем java -cp "$(cat target/classpath.txt)" DeliveryDemo.java
public class DeliveryDemo {
    static final String BOOTSTRAP = "localhost:19092,localhost:29092,localhost:39092";

    public static void main(String[] args) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP))) {
            var existing = admin.listTopics().names().get();
            for (String t : List.of("charges", "ledger")) if (existing.contains(t)) admin.deleteTopics(List.of(t)).all().get();
            Thread.sleep(1000);
            admin.createTopics(List.of(new NewTopic("charges", 1, (short) 3), new NewTopic("ledger", 1, (short) 3))).all().get();
        }
        isolation();
        consumeTransformProduce();
        fencing();
    }

    static KafkaProducer<String, String> txProducer(String txId) {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        p.put("transactional.id", txId);                     // Включает транзакции и идемпотентность.
        var producer = new KafkaProducer<String, String>(p);
        producer.initTransactions();
        return producer;
    }

    static KafkaConsumer<String, String> consumer(String group, String isolation) {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("group.id", group);
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("auto.offset.reset", "earliest");
        p.put("enable.auto.commit", false);
        p.put("isolation.level", isolation);
        return new KafkaConsumer<>(p);
    }

    static List<String> readAll(String topic, String isolation) {
        try (var c = consumer("reader-" + System.nanoTime(), isolation)) {
            c.subscribe(List.of(topic));
            List<String> out = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 4000;
            while (System.currentTimeMillis() < deadline) for (var r : c.poll(Duration.ofMillis(300))) out.add(r.value());
            return out;
        }
    }

    // ---------- транзакция: видимость для read_committed и read_uncommitted ----------
    static void isolation() throws Exception {
        try (var p = txProducer("charges-writer")) {
            p.beginTransaction();
            p.send(new ProducerRecord<>("charges", "p-1", "charge p-1 500"));
            p.send(new ProducerRecord<>("charges", "p-2", "charge p-2 700"));
            p.flush();                                         // Записи уже в логе брокера.
            p.abortTransaction();                              // Например, база не сохранила заказ.

            p.beginTransaction();
            p.send(new ProducerRecord<>("charges", "p-3", "charge p-3 900"));
            p.send(new ProducerRecord<>("charges", "p-4", "charge p-4 300"));
            p.commitTransaction();
        }
        System.out.println("== read_uncommitted sees: " + readAll("charges", "read_uncommitted"));
        System.out.println("== read_committed sees:   " + readAll("charges", "read_committed"));
    }

    // ---------- чтение, преобразование, запись и коммит смещения — одной транзакцией ----------
    static void consumeTransformProduce() throws Exception {
        String group = "ledger-builder";
        for (int attempt = 1; attempt <= 2; attempt++) {
            try (var c = consumer(group, "read_committed"); var p = txProducer("ledger-builder-0")) {
                c.subscribe(List.of("charges"));
                List<ConsumerRecord<String, String>> batch = new ArrayList<>();
                long deadline = System.currentTimeMillis() + 5000;
                while (batch.isEmpty() && System.currentTimeMillis() < deadline) c.poll(Duration.ofMillis(300)).forEach(batch::add);

                p.beginTransaction();
                Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                for (var r : batch) {
                    p.send(new ProducerRecord<>("ledger", r.key(), "debit " + r.value().substring(7)));
                    offsets.put(new TopicPartition(r.topic(), r.partition()), new OffsetAndMetadata(r.offset() + 1));
                }
                p.sendOffsetsToTransaction(offsets, c.groupMetadata());   // Смещения — часть той же транзакции.
                if (attempt == 1) {
                    p.abortTransaction();                                  // Падение до коммита.
                    System.out.println("== attempt 1: read " + batch.size() + " charges, wrote ledger, crashed before commit");
                } else {
                    p.commitTransaction();
                    System.out.println("== attempt 2: read " + batch.size() + " charges again, committed");
                }
            }
        }
        System.out.println("   ledger (read_committed):   " + readAll("ledger", "read_committed"));
        System.out.println("   ledger (read_uncommitted): " + readAll("ledger", "read_uncommitted"));
    }

    // ---------- зомби: старый экземпляр с тем же transactional.id ----------
    static void fencing() {
        var old = txProducer("ledger-builder-0");               // Экземпляр, который «завис» и считается мёртвым.
        old.beginTransaction();
        old.send(new ProducerRecord<>("ledger", "p-9", "debit zombie"));
        var fresh = txProducer("ledger-builder-0");             // Новый экземпляр после рестарта.
        try {
            old.commitTransaction();
            System.out.println("== zombie committed");
        } catch (ProducerFencedException e) {
            System.out.println("== zombie commit -> " + e.getClass().getSimpleName());
        } finally {
            old.close();
            fresh.close();
        }
    }
}
