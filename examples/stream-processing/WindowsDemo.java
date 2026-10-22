import java.time.*;
import java.util.Properties;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

// Одни и те же пять платежей по карте c-1 в трёх видах окон.
// Порог антифрода: больше трёх платежей в окне.
public class WindowsDemo {
    static final Instant T0 = Instant.parse("2026-10-22T10:00:00Z");

    public static void main(String[] args) {
        var b = new StreamsBuilder();
        KStream<String, String> payments = b.stream("payments");
        var byCard = payments.groupByKey();

        // Tumbling: минута, окна встык, каждый платёж ровно в одном окне.
        byCard.windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
              .count(Materialized.as("tumbling"));
        // Hopping: минута с шагом 30 с, окна перекрываются, платёж попадает в два.
        byCard.windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)).advanceBy(Duration.ofSeconds(30)))
              .count(Materialized.as("hopping"));
        // Session: серия, пока между платежами не больше 30 с тишины.
        byCard.windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(30)))
              .count(Materialized.as("session"));

        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "windows-demo");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);

        try (var driver = new TopologyTestDriver(b.build(), props)) {
            var in = driver.createInputTopic("payments", new StringSerializer(), new StringSerializer());
            // Время события задаём явно: оно и решает, в какое окно попадёт платёж.
            for (int sec : new int[] {5, 40, 50, 65, 80}) {
                in.pipeInput("c-1", "500", T0.plusSeconds(sec));   // ключ — карта, значение — сумма
            }

            for (String store : new String[] {"tumbling", "hopping"}) {
                System.out.println("== " + store);
                try (var it = driver.<String, Long>getWindowStore(store).all()) {
                    it.forEachRemaining(kv -> print(kv.key.window().startTime(), kv.key.window().endTime(), ")", kv.value));
                }
            }
            System.out.println("== session");
            try (var it = driver.<String, Long>getSessionStore("session").fetch("c-1")) {
                it.forEachRemaining(kv -> print(kv.key.window().startTime(), kv.key.window().endTime(), "]", kv.value));
            }
        }
    }

    static void print(Instant start, Instant end, String close, long count) {
        System.out.printf("  [%s, %s%s  count=%d%s%n", hhmmss(start), hhmmss(end), close, count, count > 3 ? "  ALERT" : "");
    }

    static String hhmmss(Instant t) { return java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(t.atOffset(ZoneOffset.UTC)); }
}
