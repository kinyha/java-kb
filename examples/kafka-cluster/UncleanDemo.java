import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

// Запуск: кластер из docker-compose.yml, затем java -cp "$(cat target/classpath.txt)" UncleanDemo.java
// Топик с min.insync.replicas=1 и unclean.leader.election.enable=true: как теряются подтверждённые записи.
public class UncleanDemo {
    static final String BOOTSTRAP = "localhost:19092,localhost:29092,localhost:39092";
    static final String TOPIC = "ledger-unsafe";

    public static void main(String[] args) throws Exception {
        int leader;
        try (Admin admin = admin()) {
            if (admin.listTopics().names().get().contains(TOPIC)) admin.deleteTopics(List.of(TOPIC)).all().get();
            Thread.sleep(1000);
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 3).configs(Map.of(
                    "min.insync.replicas", "1", "unclean.leader.election.enable", "true")))).all().get();
            Thread.sleep(2000);
            leader = describe(admin, "start");
        }
        send(3, "before");
        int[] f = java.util.stream.IntStream.rangeClosed(1, 3).filter(i -> i != leader).toArray();
        docker("stop", f[0]);
        docker("stop", f[1]);
        send(3, "only-leader");                                   // acks=all, но ISR = один лидер.
        docker("stop", leader);
        docker("start", f[0]);
        docker("start", f[1]);
        Thread.sleep(30_000);
        try (Admin admin = admin()) { describe(admin, "old leader down, followers back"); }
        docker("start", leader);
        Thread.sleep(30_000);
        try (Admin admin = admin()) { describe(admin, "old leader back"); }
        System.out.println("   acknowledged 6, readable: " + read());
    }

    static Admin admin() { return Admin.create(Map.of("bootstrap.servers", BOOTSTRAP)); }

    static int describe(Admin admin, String title) throws Exception {
        var p = admin.describeTopics(List.of(TOPIC)).allTopicNames().get().get(TOPIC).partitions().get(0);
        boolean none = p.leader() == null || p.leader().isEmpty();
        System.out.println("== " + title + ": leader=" + (none ? "none" : p.leader().id()) + " isr="
                + p.isr().stream().map(Node::id).sorted().toList());
        return none ? -1 : p.leader().id();
    }

    static void send(int n, String tag) throws Exception {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        p.put("acks", "all");
        try (var producer = new KafkaProducer<String, String>(p)) {
            for (int i = 1; i <= n; i++) {
                var m = producer.send(new ProducerRecord<>(TOPIC, "p-1", tag + "-" + i)).get();
                System.out.println("   ack " + tag + "-" + i + " offset " + m.offset());
            }
        }
    }

    static List<String> read() {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("group.id", "read-" + System.nanoTime());
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("auto.offset.reset", "earliest");
        try (var c = new KafkaConsumer<String, String>(p)) {
            c.subscribe(List.of(TOPIC));
            var out = new java.util.ArrayList<String>();
            long deadline = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < deadline) c.poll(Duration.ofMillis(300)).forEach(r -> out.add(r.value()));
            return out;
        }
    }

    static void docker(String action, int broker) throws Exception {
        new ProcessBuilder("docker", action, "kbkafka-kafka-" + broker + "-1").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        System.out.println("-- docker " + action + " kafka-" + broker);
    }
}
