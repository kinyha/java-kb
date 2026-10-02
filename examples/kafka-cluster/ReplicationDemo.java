import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

// Запуск: кластер из docker-compose.yml (проект kbkafka), затем
// java -cp "$(cat target/classpath.txt)" ReplicationDemo.java
// Демо останавливает и запускает контейнеры брокеров командой docker.
public class ReplicationDemo {
    static final String BOOTSTRAP = "localhost:19092,localhost:29092,localhost:39092";
    static final String TOPIC = "ledger-rep";

    public static void main(String[] args) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", BOOTSTRAP))) {
            if (admin.listTopics().names().get().contains(TOPIC)) admin.deleteTopics(List.of(TOPIC)).all().get();
            Thread.sleep(1000);
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 3)
                    .configs(Map.of("min.insync.replicas", "2")))).all().get();
            Thread.sleep(2000);
            describe(admin, "all brokers up");

            int leader = leader(admin);
            int[] followers = others(leader);
            System.out.println("   acks=all -> " + send("all"));

            docker("stop", followers[0]);
            Thread.sleep(15_000);                               // replica.lag.time.max.ms = 30 s; брокер ушёл сразу.
            describe(admin, "broker " + followers[0] + " stopped");
            System.out.println("   acks=all -> " + send("all"));

            docker("stop", followers[1]);
            Thread.sleep(15_000);
            describe(admin, "broker " + followers[1] + " stopped too");
            System.out.println("   acks=all -> " + send("all"));
            System.out.println("   acks=all, retries=0 -> " + send("all-noretry"));
            System.out.println("   acks=1   -> " + send("1"));

            docker("start", followers[0]);
            docker("start", followers[1]);
            Thread.sleep(25_000);
            describe(admin, "both brokers back");

            docker("stop", leader);
            Thread.sleep(15_000);
            describe(admin, "leader " + leader + " stopped");
            System.out.println("   acks=all -> " + send("all"));
            docker("start", leader);
            Thread.sleep(25_000);
            describe(admin, "broker " + leader + " back");
            System.out.println("   records readable: " + count());
        }
    }

    static void describe(Admin admin, String title) throws Exception {
        TopicDescription d = admin.describeTopics(List.of(TOPIC)).allTopicNames().get().get(TOPIC);
        var p = d.partitions().get(0);
        System.out.println("== " + title + ": leader=" + (p.leader() == null || p.leader().isEmpty() ? "none" : p.leader().id())
                + " replicas=" + ids(p.replicas()) + " isr=" + ids(p.isr()));
    }

    static int leader(Admin admin) throws Exception {
        return admin.describeTopics(List.of(TOPIC)).allTopicNames().get().get(TOPIC).partitions().get(0).leader().id();
    }

    static int[] others(int leader) {
        return java.util.stream.IntStream.rangeClosed(1, 3).filter(i -> i != leader).toArray();
    }

    static List<Integer> ids(List<Node> nodes) { return nodes.stream().map(Node::id).sorted().toList(); }

    static String send(String acks) {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", StringSerializer.class.getName());
        boolean noRetry = acks.equals("all-noretry");
        p.put("acks", noRetry ? "all" : acks);
        p.put("enable.idempotence", acks.equals("all"));
        if (noRetry) p.put("retries", 0);                      // Увидеть ошибку брокера без повторов.
        p.put("delivery.timeout.ms", 8000);
        p.put("request.timeout.ms", 5000);
        try (var producer = new KafkaProducer<String, String>(p)) {
            var m = producer.send(new ProducerRecord<>(TOPIC, "p-1", "entry")).get();
            return "ok, offset " + m.offset();
        } catch (ExecutionException e) {
            return e.getCause().getClass().getSimpleName();
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }

    static int count() {
        Properties p = new Properties();
        p.put("bootstrap.servers", BOOTSTRAP);
        p.put("group.id", "count-" + System.nanoTime());
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", StringDeserializer.class.getName());
        p.put("auto.offset.reset", "earliest");
        try (var c = new KafkaConsumer<String, String>(p)) {
            c.subscribe(List.of(TOPIC));
            int n = 0;
            long deadline = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < deadline) n += c.poll(Duration.ofMillis(300)).count();
            return n;
        }
    }

    static void docker(String action, int broker) throws Exception {
        new ProcessBuilder("docker", action, "kbkafka-kafka-" + broker + "-1").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        System.out.println("-- docker " + action + " kafka-" + broker);
    }
}
