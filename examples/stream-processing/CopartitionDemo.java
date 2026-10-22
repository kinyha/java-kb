import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

// Join платежей со статусами карт, когда у топиков разное число партиций.
// Запуск: java -cp "$(cat target/classpath.txt)" CopartitionDemo.java 3   — партиций поровну
//         java -cp "$(cat target/classpath.txt)" CopartitionDemo.java 2   — у cards на одну меньше
public class CopartitionDemo {
    public static void main(String[] args) throws Exception {
        int cardPartitions = Integer.parseInt(args[0]);
        var broker = new EmbeddedKafkaKraftBroker(1, 3, "payments", "enriched");   // payments: 3 партиции
        broker.brokerProperties(Map.of("group.initial.rebalance.delay.ms", "0"));
        broker.afterPropertiesSet();
        broker.addTopics(new NewTopic("cards", cardPartitions, (short) 1));
        String bootstrap = broker.getBrokersAsString();

        var pp = new Properties();
        pp.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        try (var p = new KafkaProducer<>(pp, new StringSerializer(), new StringSerializer())) {
            for (int i = 1; i <= 6; i++) p.send(new ProducerRecord<>("cards", "c-" + i, "active"));   // статус есть у всех карт
            p.flush();
            Thread.sleep(200);
            for (int i = 1; i <= 6; i++) p.send(new ProducerRecord<>("payments", "c-" + i, "p-" + i));
        }

        var b = new StreamsBuilder();
        KTable<String, String> cards = b.table("cards", Consumed.with(Serdes.String(), Serdes.String()));
        b.stream("payments", Consumed.with(Serdes.String(), Serdes.String()))
         .leftJoin(cards, (payment, status) -> payment + " card=" + status)
         .to("enriched", Produced.with(Serdes.String(), Serdes.String()));

        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "enrich");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("copartition-demo").toString());
        try (var streams = new KafkaStreams(b.build(), props)) {
            streams.start();
            Thread.sleep(10_000);
            System.out.println("state: " + streams.state());
            var tasks = new TreeMap<String, String>();
            streams.metadataForLocalThreads().forEach(t -> t.activeTasks().forEach(task ->
                tasks.put(task.taskId().toString(), new TreeSet<>(task.topicPartitions().stream().map(Object::toString).toList()).toString())));
            System.out.println("tasks: " + tasks);
            System.out.println("enriched: " + read(bootstrap));
            streams.close(Duration.ofSeconds(5));
        } finally {
            broker.destroy();
        }
    }

    static Set<String> read(String bootstrap) {
        var cp = new Properties();
        cp.put("bootstrap.servers", bootstrap);
        cp.put("group.id", "reader");
        cp.put("auto.offset.reset", "earliest");
        try (var c = new KafkaConsumer<>(cp, new StringDeserializer(), new StringDeserializer())) {
            c.subscribe(List.of("enriched"));
            var out = new TreeSet<String>();
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) c.poll(Duration.ofMillis(200)).forEach(r -> out.add(r.value()));
            return out;
        }
    }
}
