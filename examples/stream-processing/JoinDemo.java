import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.*;

// Обогащение: к каждому платежу приклеить статус карты из топика cards.
// Один и тот же вход прогоняем через обычную KTable и через версионированную.
public class JoinDemo {
    static final Instant T0 = Instant.parse("2026-10-22T10:00:00Z");
    static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    public static void main(String[] args) {
        run("plain KTable", Materialized.as(Stores.persistentKeyValueStore("cards")));
        run("versioned KTable", Materialized.as(Stores.persistentVersionedKeyValueStore("cards", Duration.ofDays(1))));
    }

    static void run(String title, Materialized<String, String, KeyValueStore<Bytes, byte[]>> store) {
        var b = new StreamsBuilder();
        KTable<String, String> cards = b.table("cards", Consumed.with(Serdes.String(), Serdes.String()), store);
        b.<String, String>stream("payments")                                   // ключ — карта, как и у cards
         .leftJoin(cards, (payment, status) -> payment + " card=" + status)    // поиск в таблице в момент платежа
         .to("enriched");

        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "join-demo");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);

        System.out.println("== " + title);
        try (var driver = new TopologyTestDriver(b.build(), props)) {
            var s = new StringSerializer();
            var cardsIn = driver.createInputTopic("cards", s, s);
            var payIn = driver.createInputTopic("payments", s, s);
            var out = driver.createOutputTopic("enriched", new StringDeserializer(), new StringDeserializer());

            cardsIn.pipeInput("c-1", "active", at(0));
            payIn.pipeInput("c-1", "p-1@" + hms(10), at(10));
            cardsIn.pipeInput("c-1", "blocked", at(60));      // карту заблокировали в 10:01:00
            payIn.pipeInput("c-1", "p-2@" + hms(70), at(70));
            payIn.pipeInput("c-1", "p-3@" + hms(50), at(50)); // опоздал: совершён до блокировки
            payIn.pipeInput("c-2", "p-4@" + hms(80), at(80)); // профиля c-2 ещё нет
            cardsIn.pipeInput("c-2", "active", at(85));       // профиль пришёл после платежа

            out.readValuesToList().forEach(v -> System.out.println("  " + v));
        }
    }

    static Instant at(int sec) { return T0.plusSeconds(sec); }
    static String hms(int sec) { return HMS.format(at(sec)); }
}
