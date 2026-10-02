import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

// Запуск: кластер из docker-compose.yml, затем java -cp "$(cat target/classpath.txt)" OpsDemo.java
public class OpsDemo {
    static final String BOOTSTRAP = "localhost:19092,localhost:29092,localhost:39092";

    public static void main(String[] args) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP))) {
            for (String t : List.of("account-balance", "audit", "keyed")) {
                if (admin.listTopics().names().get().contains(t)) admin.deleteTopics(List.of(t)).all().get();
            }
            Thread.sleep(1500);
            compaction(admin);
            retention(admin);
            partitions(admin);
        }
    }

    // ---------- compaction: остаётся последнее значение ключа ----------
    static void compaction(Admin admin) throws Exception {
        admin.createTopics(List.of(new NewTopic("account-balance", 1, (short) 3).configs(Map.of(
                "cleanup.policy", "compact",
                "segment.ms", "1000",                       // Часто закрывать сегменты: чистится только закрытый.
                "min.cleanable.dirty.ratio", "0.01",
                "delete.retention.ms", "1000",              // Сколько хранить tombstone.
                "min.compaction.lag.ms", "0")))).all().get();
        try (var p = producer()) {
            String[][] writes = {{"acc-1", "100"}, {"acc-2", "50"}, {"acc-1", "80"}, {"acc-3", "10"}, {"acc-1", "130"},
                    {"acc-2", null}, {"acc-3", "15"}};    // null — tombstone: удалить ключ.
            for (String[] w : writes) p.send(new ProducerRecord<>("account-balance", w[0], w[1])).get();
        }
        System.out.println("== compaction, before: " + read("account-balance"));
        Thread.sleep(2000);
        try (var p = producer()) { p.send(new ProducerRecord<>("account-balance", "acc-9", "0")).get(); } // Закрыть сегмент.
        Thread.sleep(45_000);                               // log.cleaner.backoff.ms = 15 s; даём чистильщику пройти.
        try (var p = producer()) { p.send(new ProducerRecord<>("account-balance", "acc-9", "1")).get(); }
        Thread.sleep(20_000);
        System.out.println("== compaction, after:  " + read("account-balance"));
    }

    // ---------- retention: отставшая группа теряет данные ----------
    static void retention(Admin admin) throws Exception {
        admin.createTopics(List.of(new NewTopic("audit", 1, (short) 3).configs(Map.of(
                "retention.ms", "5000", "segment.ms", "1000")))).all().get();
        try (var p = producer()) { for (int i = 0; i < 10; i++) p.send(new ProducerRecord<>("audit", "e", "event-" + i)).get(); }
        try (var c = consumer("audit-reader")) {             // Прочитал 3 и закоммитил смещение 3.
            c.assign(List.of(new TopicPartition("audit", 0)));
            c.seek(new TopicPartition("audit", 0), 0);
            var recs = c.poll(Duration.ofSeconds(5));
            c.commitSync(Map.of(new TopicPartition("audit", 0), new org.apache.kafka.clients.consumer.OffsetAndMetadata(3)));
            System.out.println("== retention: group committed offset 3 of " + recs.count() + " read");
        }
        Thread.sleep(8000);
        try (var p = producer()) { for (int i = 10; i < 12; i++) p.send(new ProducerRecord<>("audit", "e", "event-" + i)).get(); }
        Thread.sleep(20_000);                               // В compose проверка retention раз в 5 с.
        var tp = new TopicPartition("audit", 0);
        long start = admin.listOffsets(Map.of(tp, OffsetSpec.earliest())).all().get().get(tp).offset();
        System.out.println("   log start offset now " + start);
        try (var c = consumer("audit-reader")) {
            c.subscribe(List.of("audit"));
            List<String> got = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < deadline) c.poll(Duration.ofMillis(300)).forEach(r -> got.add(r.value()));
            System.out.println("   group resumes and reads: " + got);
        }
    }

    // ---------- число партиций: ключи переезжают ----------
    static void partitions(Admin admin) throws Exception {
        admin.createTopics(List.of(new NewTopic("keyed", 6, (short) 3))).all().get();
        try (var p = producer()) {
            System.out.print("== key partition with 6:  ");
            for (String k : List.of("p-1", "p-2", "p-3", "p-4")) System.out.print(k + "=" + p.send(new ProducerRecord<>("keyed", k, "v")).get().partition() + " ");
            System.out.println();
        }
        admin.createPartitions(Map.of("keyed", NewPartitions.increaseTo(12))).all().get();
        Thread.sleep(3000);
        try (var p = producer()) {
            System.out.print("== key partition with 12: ");
            for (String k : List.of("p-1", "p-2", "p-3", "p-4")) System.out.print(k + "=" + p.send(new ProducerRecord<>("keyed", k, "v")).get().partition() + " ");
            System.out.println();
        }
        try {
            admin.createPartitions(Map.of("keyed", NewPartitions.increaseTo(6))).all().get();
        } catch (Exception e) {
            System.out.println("== decrease to 6 -> " + e.getCause().getClass().getSimpleName());
        }
    }

    static KafkaProducer<String, String> producer() {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        return new KafkaProducer<>(p);
    }

    static KafkaConsumer<String, String> consumer(String group) {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("group.id", group);
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("auto.offset.reset", "earliest");
        p.put("enable.auto.commit", false);
        return new KafkaConsumer<>(p);
    }

    static List<String> read(String topic) {
        try (var c = consumer("read-" + System.nanoTime())) {
            c.subscribe(List.of(topic));
            List<String> out = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline)
                c.poll(Duration.ofMillis(300)).forEach(r -> out.add(r.offset() + ":" + r.key() + "=" + r.value()));
            return out;
        }
    }
}
