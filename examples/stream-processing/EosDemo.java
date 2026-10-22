import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

// Счётчик платежей по карте падает после обработки, но до коммита, и задачу забирает другой экземпляр.
// Запуск: java -cp "$(cat target/classpath.txt)" EosDemo.java at_least_once
//         java -cp "$(cat target/classpath.txt)" EosDemo.java exactly_once_v2
// Брокер живёт в этой JVM, экземпляры приложения A и B — отдельные JVM: A убивается через halt.
public class EosDemo {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("worker")) { worker(args[1], args[2], args[3], args[4]); return; }
        String guarantee = args[0];

        var broker = new EmbeddedKafkaKraftBroker(1, 1, "payments", "card-totals");
        broker.brokerProperties(Map.of(
            "transaction.state.log.replication.factor", "1",
            "transaction.state.log.min.isr", "1",
            "group.initial.rebalance.delay.ms", "0"));
        broker.afterPropertiesSet();
        String bootstrap = broker.getBrokersAsString();
        try {
            var pp = new Properties();
            pp.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            try (var p = new KafkaProducer<>(pp, new StringSerializer(), new StringSerializer())) {
                p.send(new ProducerRecord<>("payments", "c-1", "p-1 500"));
                p.send(new ProducerRecord<>("payments", "c-1", "p-2 700"));
            }

            Path dir = Files.createTempDirectory("eos-demo");
            spawn(bootstrap, guarantee, "crash", dir.resolve("A"));     // экземпляр A: обработал и умер
            spawn(bootstrap, guarantee, "recover", dir.resolve("B"));   // экземпляр B: пустой диск, другая машина

            System.out.println("card-totals read_uncommitted: " + read(bootstrap, "card-totals", "read_uncommitted"));
            System.out.println("card-totals read_committed:   " + read(bootstrap, "card-totals", "read_committed"));
            broker.doWithAdmin(admin -> {
                try {
                    var changelog = new ConfigResource(ConfigResource.Type.TOPIC, "antifraud-totals-changelog");
                    var cfg = admin.describeConfigs(List.of(changelog)).all().get().get(changelog);
                    System.out.println("antifraud-totals-changelog cleanup.policy=" + cfg.get("cleanup.policy").value());
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        } finally {
            broker.destroy();
        }
    }

    // Экземпляр приложения antifraud: считает платежи по карте и пишет итог в card-totals.
    static void worker(String bootstrap, String guarantee, String mode, String stateDir) throws Exception {
        String me = mode.equals("crash") ? "A" : "B";
        var b = new StreamsBuilder();
        var processed = new AtomicInteger();
        b.<String, String>stream("payments")
         .groupByKey()
         .count(Materialized.as("totals"))                       // store totals, changelog antifraud-totals-changelog
         .toStream()
         .peek((card, n) -> { System.out.println(me + ": " + card + " count=" + n); processed.incrementAndGet(); })
         .to("card-totals", Produced.with(Serdes.String(), Serdes.Long()));

        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "antifraud");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, guarantee);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10_000);     // A упадёт раньше коммита; больше нельзя:
                                                                    // в EOS интервал не выше transaction.timeout.ms (10 с)
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);  // без кеша: каждое обновление сразу в changelog
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG), 6_000);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);

        var streams = new KafkaStreams(b.build(), props);
        streams.setGlobalStateRestoreListener(new org.apache.kafka.streams.processor.StateRestoreListener() {
            public void onRestoreStart(org.apache.kafka.common.TopicPartition tp, String store, long from, long to) {}
            public void onBatchRestored(org.apache.kafka.common.TopicPartition tp, String store, long end, long n) {}
            public void onRestoreEnd(org.apache.kafka.common.TopicPartition tp, String store, long total) {
                System.out.println(me + ": restored " + total + " records of " + tp.topic());
            }
        });
        streams.start();

        while (processed.get() < 2) Thread.sleep(50);
        Thread.sleep(1_000);                                      // записи ушли на брокер, коммита ещё не было
        if (mode.equals("crash")) {
            System.out.println("A: crash before commit");
            Runtime.getRuntime().halt(1);                         // kill -9: ни коммита, ни закрытия
        }
        var store = streams.store(StoreQueryParameters.fromNameAndType("totals", QueryableStoreTypes.<String, Long>keyValueStore()));
        System.out.println("B: store totals[c-1]=" + store.get("c-1"));
        streams.close();                                          // чистое закрытие: коммит
    }

    static void spawn(String bootstrap, String guarantee, String mode, Path stateDir) throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "EosDemo.java",
                "worker", bootstrap, guarantee, mode, stateDir.toString())
            .inheritIO().start().waitFor();
    }

    static List<Long> read(String bootstrap, String topic, String isolation) {
        var cp = new Properties();
        cp.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        cp.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolation);
        try (var c = new KafkaConsumer<>(cp, new StringDeserializer(), new LongDeserializer())) {
            var tp = new org.apache.kafka.common.TopicPartition(topic, 0);
            c.assign(List.of(tp));
            c.seekToBeginning(List.of(tp));
            var values = new ArrayList<Long>();
            long deadline = System.currentTimeMillis() + 3_000;
            while (System.currentTimeMillis() < deadline) c.poll(Duration.ofMillis(200)).forEach(r -> values.add(r.value()));
            return values;
        }
    }
}
