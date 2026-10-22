import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

// Антифрод: больше трёх платежей по одной карте за минуту — тревога.
// Вход: топик payments из 3 партиций, ключ — id платежа, значение — "карта:сумма".
// Печатает топологию, задачи экземпляра и служебные топики, которые Streams создал сам.
public class TopologyDemo {
    public static void main(String[] args) throws Exception {
        var b = new StreamsBuilder();
        b.stream("payments", Consumed.with(Serdes.String(), Serdes.String()))
            .filter((paymentId, v) -> amount(v) > 0)                     // stateless: смотрит на одну запись
            .selectKey((paymentId, v) -> card(v))                        // ключ теперь — карта
            .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))  // группировать по новому ключу
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
            .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("card-counts"))  // stateful
            .toStream()
            .filter((window, count) -> count > 3)
            .map((window, count) -> KeyValue.pair(window.key(), "payments in 1m: " + count))
            .to("fraud-alerts", Produced.with(Serdes.String(), Serdes.String()));
        Topology topology = b.build();
        System.out.println(topology.describe());

        var broker = new EmbeddedKafkaKraftBroker(1, 3, "payments", "fraud-alerts");
        broker.brokerProperties(Map.of("group.initial.rebalance.delay.ms", "0"));
        broker.afterPropertiesSet();
        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "antifraud");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("topology-demo").toString());
        try (var streams = new KafkaStreams(topology, props)) {
            streams.start();
            while (streams.state() != KafkaStreams.State.RUNNING) Thread.sleep(100);
            var tasks = new TreeSet<String>();
            streams.metadataForLocalThreads().forEach(t -> t.activeTasks().forEach(task -> tasks.add(task.taskId().toString())));
            System.out.println("active tasks: " + tasks);
            broker.doWithAdmin(admin -> {
                try {
                    for (String topic : new TreeSet<>(admin.listTopics().names().get())) {
                        var res = new ConfigResource(ConfigResource.Type.TOPIC, topic);
                        var cfg = admin.describeConfigs(List.of(res)).all().get().get(res);
                        System.out.printf("%-34s cleanup.policy=%-14s retention.ms=%s%n",
                            topic, cfg.get("cleanup.policy").value(), cfg.get("retention.ms").value());
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            streams.close(Duration.ofSeconds(5));
        } finally {
            broker.destroy();
        }
    }

    static String card(String v)  { return v.split(":")[0]; }
    static long amount(String v)  { return Long.parseLong(v.split(":")[1]); }
}
