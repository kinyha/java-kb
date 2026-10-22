import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// Запуск: java examples/sd-chat/ChatDemo.java (JDK 21, без зависимостей).
// Сервер чата, шлюзы и сеть смоделированы в одном процессе: доставка — задачи в пуле потоков со случайной задержкой.
public class ChatDemo {

    public static void main(String[] args) throws Exception {
        clocks();
        parallelDelivery();
        retry();
        cursors();
    }

    // ---------- 1. Чьи часы задают порядок ----------

    record Msg(String from, String text, long senderClock, long serverClock, long seq) {}

    static void clocks() {
        System.out.println("== anna asks, a bot answers in 20 ms, boris answers in 2 s");
        long t0 = 43_200_000;                           // 12:00:00.000 по настоящему времени
        int chat2Skew = -50;                            // часы экземпляра chat-2 отстают от chat-1 на 50 мс
        int borisPhoneSkew = -3000;                     // часы телефона Бориса отстают на 3 с
        List<Msg> chat = List.of(
                // Анна: часы точные, сообщение принял chat-1 через 40 мс; seq выдаёт владелец чата.
                new Msg("anna", "Встреча в 7?", t0, t0 + 40, 1),
                // Бот получил вопрос и ответил через 20 мс; его сообщение принял chat-2.
                new Msg("bot", "Создал событие на 19:00", t0 + 60, t0 + 65 + chat2Skew, 2),
                // Борис ответил через 2 с; сообщение принял chat-1.
                new Msg("boris", "Давай в 8", t0 + 2000 + borisPhoneSkew, t0 + 2040, 3));
        System.out.println("   " + String.join(" | ", chat.stream().map(m -> m.from() + ": " + m.text()).toList()));
        print("by sender clock", chat, Msg::senderClock);
        print("by server clock", chat, Msg::serverClock);
        print("by chat seq", chat, Msg::seq);
    }

    static void print(String title, List<Msg> chat, java.util.function.ToLongFunction<Msg> key) {
        List<String> row = new ArrayList<>();
        for (Msg m : chat.stream().sorted(Comparator.comparingLong(key)).toList()) {
            long k = key.applyAsLong(m);
            row.add(m.from() + " " + (k < 100 ? "#" + k
                    : String.format("%02d:%02d:%02d.%03d", k / 3_600_000, k / 60_000 % 60, k / 1000 % 60, k % 1000)));
        }
        System.out.printf("   %-16s  %s%n", title, String.join(" | ", row));
    }

    // ---------- 2. Параллельная доставка в группу ----------

    // Лог чата: один писатель выдаёт seq и дописывает сообщение. Это «владелец чата».
    static final class ChatLog {
        private final List<String> messages = new ArrayList<>();
        synchronized long append(String text) { messages.add(text); return messages.size(); }
    }

    static void parallelDelivery() throws Exception {
        int runs = 200, perSender = 50;
        int arrivalDiffers = 0, seqDiffers = 0;
        long gaps = 0; int maxHeld = 0;
        try (ExecutorService net = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int run = 0; run < runs; run++) {
                ChatLog log = new ChatLog();
                // Экран получателя: сообщения в порядке прихода.
                List<Long> vera = Collections.synchronizedList(new ArrayList<>());
                List<Long> gleb = Collections.synchronizedList(new ArrayList<>());
                CountDownLatch delivered = new CountDownLatch(2 * 2 * perSender);
                Runnable sender = () -> {
                    for (int i = 0; i < perSender; i++) {
                        long seq = log.append("msg");
                        // Fan-out: каждому получателю — своя доставка со своей задержкой.
                        for (List<Long> screen : List.of(vera, gleb)) {
                            net.submit(() -> {
                                sleepMicros(ThreadLocalRandom.current().nextInt(2000));
                                screen.add(seq);
                                delivered.countDown();
                            });
                        }
                        sleepMicros(ThreadLocalRandom.current().nextInt(300));
                    }
                };
                Thread anna = Thread.ofVirtual().start(sender), boris = Thread.ofVirtual().start(sender);
                anna.join(); boris.join(); delivered.await();

                if (!vera.equals(gleb)) arrivalDiffers++;
                // Клиент показывает по seq: вставляет на своё место, а дыру держит, пока она не закроется.
                List<Long> veraBySeq = vera.stream().sorted().toList(), glebBySeq = gleb.stream().sorted().toList();
                if (!veraBySeq.equals(glebBySeq)) seqDiffers++;
                for (List<Long> screen : List.of(vera, gleb)) {
                    long expected = 1; TreeSet<Long> held = new TreeSet<>();
                    for (long seq : screen) {
                        if (seq != expected) { if (seq > expected) { gaps++; held.add(seq); } continue; }
                        expected++;
                        while (held.remove(expected)) expected++;   // дыра закрылась — показываем накопленное
                        maxHeld = Math.max(maxHeld, held.size());
                    }
                    maxHeld = Math.max(maxHeld, held.size());
                }
            }
        }
        System.out.printf("== group chat: anna and boris send %d messages each, vera and gleb receive, %d runs%n", perSender, runs);
        System.out.printf("   screens in arrival order differ:   %3d of %d runs%n", arrivalDiffers, runs);
        System.out.printf("   screens ordered by seq differ:     %3d of %d runs%n", seqDiffers, runs);
        System.out.printf("   gaps a client noticed: %.1f per 100 messages, at most %d messages waiting behind a gap%n",
                gaps * 100.0 / (runs * 2.0 * 2 * perSender), maxHeld);
    }

    static void sleepMicros(int micros) {
        try { Thread.sleep(Duration.ofNanos(micros * 1000L)); } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    // ---------- 3. Повтор после потерянного ack ----------

    static final class ChatServer {
        final List<String> log = new ArrayList<>();
        final Map<String, Long> byClientId = new HashMap<>();   // (chat, client_msg_id) -> seq
        final boolean dedup;
        ChatServer(boolean dedup) { this.dedup = dedup; }

        long send(String chat, String clientMsgId, String text) {
            if (dedup) {
                Long seen = byClientId.get(chat + "/" + clientMsgId);
                if (seen != null) return seen;                  // повтор: тот же seq, что в первый раз
            }
            log.add(text);
            long seq = log.size();
            byClientId.put(chat + "/" + clientMsgId, seq);
            return seq;
        }
    }

    static void retry() {
        System.out.println("== anna's ack is lost; boris writes; anna's client reconnects and resends c-81");
        for (boolean dedup : new boolean[]{false, true}) {
            ChatServer s = new ChatServer(dedup);
            s.send("anna-boris", "c-81", "anna: Купи хлеб");       // записано, но ack до Анны не дошёл
            s.send("anna-boris", "b-17", "boris: Уже иду");
            long ack = s.send("anna-boris", "c-81", "anna: Купи хлеб"); // повтор с тем же client_msg_id
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.log.size(); i++) sb.append(" #").append(i + 1).append(' ').append(s.log.get(i)).append(';');
            System.out.printf("   %-22s retry ack seq=%d, chat:%s%n", dedup ? "dedup by client_msg_id" : "no dedup", ack, sb);
        }
    }

    // ---------- 4. Курсоры устройств и статусы ----------

    // Сервер хранит не статус каждого сообщения, а три числа на участника чата.
    static long lastSeq = 3, annaDelivered = 3, annaRead = 3;
    static final Map<String, Long> deviceCursor = new LinkedHashMap<>(Map.of("phone", 3L, "laptop", 3L));
    static final Set<String> online = new HashSet<>(Set.of("phone"));

    static void cursors() {
        System.out.println("== anna has a phone (online) and a laptop (offline), both synced up to #3");
        borisSends(3);
        online.remove("phone");
        System.out.println("   -- phone loses network");
        borisSends(1);
        connect("laptop");
        annaRead = 6;                                       // прочитала на ноутбуке до #6
        System.out.println("   laptop: anna reads up to #6 -> read_up_to=6, boris sees " + statuses());
        connect("phone");
        System.out.println("   phone unread badge: " + (lastSeq - annaRead));
    }

    static void borisSends(int n) {
        for (int i = 0; i < n; i++) {
            long seq = ++lastSeq;
            List<String> got = new ArrayList<>();
            for (String d : online) { deviceCursor.put(d, seq); got.add(d); }   // живая доставка + ack устройства
            if (!got.isEmpty()) annaDelivered = seq;
            System.out.printf("   boris sends #%d -> %s%n", seq, got.isEmpty() ? "no live device, push via APNs/FCM" : "live to " + got);
        }
        System.out.println("   boris sees " + statuses());
    }

    static void connect(String device) {
        long after = deviceCursor.get(device);
        online.add(device);
        StringBuilder sb = new StringBuilder();
        for (long s = after + 1; s <= lastSeq; s++) sb.append(" #").append(s);
        deviceCursor.put(device, lastSeq);
        annaDelivered = Math.max(annaDelivered, lastSeq);
        System.out.printf("   %s connects with after=%d, gets:%s; read_up_to=%d%n", device, after, sb, annaRead);
    }

    static String statuses() {
        StringBuilder sb = new StringBuilder();
        for (long s = 4; s <= lastSeq; s++)
            sb.append(" #").append(s).append(s <= annaRead ? " read" : s <= annaDelivered ? " delivered" : " sent");
        return sb.toString().trim();
    }
}
