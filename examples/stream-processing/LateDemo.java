import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

// Минутное окно с grace 10 с. Платежи приходят не по порядку времени события.
// Смотрим две вещи: промежуточные обновления счётчика и итог окна через suppress.
public class LateDemo {
    static final Instant T0 = Instant.parse("2026-10-22T10:00:00Z");
    static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    public static void main(String[] args) {
        var b = new StreamsBuilder();
        KTable<Windowed<String>, Long> counts = b.<String, String>stream("payments")
            .groupByKey()
            .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(1), Duration.ofSeconds(10)))
            .count(Materialized.with(Serdes.String(), Serdes.Long()));   // serde нужны буферу suppress
        counts.toStream().to("updates", Produced.with(WindowedSerdes.timeWindowedSerdeFrom(String.class, 60_000), Serdes.Long()));
        counts.suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))   // держать до закрытия окна
              .toStream().to("final", Produced.with(WindowedSerdes.timeWindowedSerdeFrom(String.class, 60_000), Serdes.Long()));

        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "late-demo");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);

        try (var driver = new TopologyTestDriver(b.build(), props)) {
            var in = driver.createInputTopic("payments", new StringSerializer(), new StringSerializer());
            var wd = WindowedSerdes.timeWindowedSerdeFrom(String.class, 60_000).deserializer();
            var updates = driver.createOutputTopic("updates", wd, new LongDeserializer());
            var fin = driver.createOutputTopic("final", wd, new LongDeserializer());

            // Секунды от 10:00:00 в порядке прихода: 55 и 58 опоздали.
            for (int sec : new int[] {5, 40, 65, 55, 90, 58}) {
                Instant t = T0.plusSeconds(sec);
                in.pipeInput("c-1", "500", t);
                System.out.printf("in %s:", HMS.format(t));
                boolean any = false;
                for (var r : updates.readKeyValuesToList()) { System.out.printf("  update %s=%d", win(r.key), r.value); any = true; }
                for (var r : fin.readKeyValuesToList())     { System.out.printf("  FINAL %s=%d", win(r.key), r.value); any = true; }
                System.out.println(any ? "" : "  dropped");
            }

            driver.advanceWallClockTime(Duration.ofHours(1));   // настенное время окна не закрывает
            System.out.println("after +1h wall clock: final=" + fin.readKeyValuesToList());
            driver.metrics().forEach((name, m) -> {
                if (name.name().equals("dropped-records-total")) System.out.println("dropped-records-total=" + m.metricValue());
            });
        }
    }

    static String win(Windowed<String> w) {
        return "[" + HMS.format(w.window().startTime()) + "," + HMS.format(w.window().endTime()) + ")";
    }
}
