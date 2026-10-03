import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import redis.clients.jedis.RedisClient;

// Запуск: Redis 8 на localhost:6379 (см. README), затем java -cp "$(cat target/classpath.txt)" CommunicationDemo.java
// Сервис заказов сообщает складу о заказе: синхронным HTTP-вызовом или сообщением в очередь (список Redis).
public class CommunicationDemo {
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build();
    static final RedisClient redis = RedisClient.create("redis://localhost:6379");
    static volatile HttpServer inventory;
    static final AtomicInteger reserved = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        System.out.println("== 40 orders at 10/s; inventory is down from 1.0 s to 3.0 s");
        sync();
        async();
        System.out.println("== order page needs profile, orders and bonuses, 50 ms each");
        composition();
        System.out.println("== chain A -> B -> C: A waits 300 ms, C needs 500 ms; 10 requests");
        deadlines(false);
        deadlines(true);
        System.exit(0);
    }

    static void startInventory() throws Exception {
        inventory = HttpServer.create(new InetSocketAddress(8201), 50);
        inventory.createContext("/reserve", ex -> { reserved.incrementAndGet(); ex.sendResponseHeaders(204, -1); ex.close(); });
        inventory.start();
    }

    // Склад недоступен с 1-й по 3-ю секунду.
    static void outage() {
        Thread.ofPlatform().start(() -> {
            try { Thread.sleep(1000); inventory.stop(0); Thread.sleep(2000); startInventory(); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    static void sync() throws Exception {
        reserved.set(0); startInventory(); outage();
        int ok = 0, failed = 0;
        for (int i = 0; i < 40; i++) {
            long due = System.nanoTime() + 100_000_000;
            try {
                var r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:8201/reserve")).timeout(Duration.ofMillis(300))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                if (r.statusCode() == 204) ok++; else failed++;
            } catch (Exception e) { failed++; }                      // заказ не создан: склад не ответил
            while (System.nanoTime() < due) Thread.onSpinWait();
        }
        Thread.sleep(500);
        System.out.printf("   sync HTTP:    orders accepted %2d, rejected %2d, reserved %2d%n", ok, failed, reserved.get());
        inventory.stop(0);
    }

    static void async() throws Exception {
        reserved.set(0); startInventory(); outage();
        redis.del("inventory-queue");
        AtomicLong maxDelay = new AtomicLong();
        // Потребитель склада: читает очередь и резервирует; пока склад лежит, сообщения ждут в очереди.
        Thread consumer = Thread.ofPlatform().daemon().start(() -> {
            while (true) {
                List<String> m = redis.brpop(1, "inventory-queue");
                if (m == null) continue;
                long sentAt = Long.parseLong(m.get(1));
                while (true) {
                    try {
                        var r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:8201/reserve")).timeout(Duration.ofMillis(300))
                                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                        if (r.statusCode() == 204) break;
                    } catch (Exception e) { try { Thread.sleep(200); } catch (InterruptedException ie) { return; } }   // повтор
                }
                maxDelay.accumulateAndGet(System.currentTimeMillis() - sentAt, Math::max);
            }
        });
        int ok = 0;
        for (int i = 0; i < 40; i++) {
            long due = System.nanoTime() + 100_000_000;
            redis.lpush("inventory-queue", String.valueOf(System.currentTimeMillis()));   // заказ принят сразу
            ok++;
            while (System.nanoTime() < due) Thread.onSpinWait();
        }
        while (redis.llen("inventory-queue") > 0 || reserved.get() < 40) Thread.sleep(50);
        System.out.printf("   async queue:  orders accepted %2d, rejected  0, reserved %2d, max delay to reserve %.1f s%n",
                ok, reserved.get(), maxDelay.get() / 1000.0);
        inventory.stop(0);
    }

    static void composition() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(8202), 50);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        for (String p : List.of("/profile", "/orders", "/bonuses"))
            s.createContext(p, ex -> { try { Thread.sleep(50); } catch (InterruptedException ignored) { } ex.sendResponseHeaders(204, -1); ex.close(); });
        s.start();
        for (int warm = 0; warm < 2; warm++) {
            long t = System.nanoTime();
            for (String p : List.of("/profile", "/orders", "/bonuses")) call(p);
            long seq = (System.nanoTime() - t) / 1_000_000;
            t = System.nanoTime();
            var fs = List.of("/profile", "/orders", "/bonuses").stream().map(p -> CompletableFuture.runAsync(() -> call(p))).toList();
            CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).join();
            long par = (System.nanoTime() - t) / 1_000_000;
            if (warm == 1) {
                System.out.println("   sequential: " + (seq / 10 * 10) + "+ ms");
                System.out.println("   parallel:   " + (par / 10 * 10) + "+ ms");
            }
        }
        s.stop(0);
    }

    // B вызывает C. Без передачи дедлайна — со своим тайм-аутом 1 с; с передачей — с остатком из заголовка.
    static void deadlines(boolean propagate) throws Exception {
        AtomicInteger cDone = new AtomicInteger(), cRefused = new AtomicInteger();
        HttpServer c = HttpServer.create(new InetSocketAddress(8204), 50);
        c.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        c.createContext("/", ex -> {
            String d = ex.getRequestHeaders().getFirst("X-Deadline");
            if (d != null && Long.parseLong(d) - System.currentTimeMillis() < 500) {   // не успею — не начинаю
                cRefused.incrementAndGet(); ex.sendResponseHeaders(504, -1); ex.close(); return;
            }
            try { Thread.sleep(500); } catch (InterruptedException ignored) { }
            cDone.incrementAndGet();
            ex.sendResponseHeaders(204, -1); ex.close();
        });
        c.start();
        HttpServer b = HttpServer.create(new InetSocketAddress(8203), 50);
        b.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        b.createContext("/", ex -> {
            String d = ex.getRequestHeaders().getFirst("X-Deadline");
            var req = HttpRequest.newBuilder(URI.create("http://localhost:8204/")).timeout(Duration.ofSeconds(1));
            if (propagate && d != null) req.header("X-Deadline", d);
            int code = 502;
            try { code = http.send(req.build(), HttpResponse.BodyHandlers.discarding()).statusCode(); } catch (Exception ignored) { }
            ex.sendResponseHeaders(code, -1); ex.close();                              // статус C — дальше к A
        });
        b.start();
        int aTimeouts = 0, aFast504 = 0;
        for (int i = 0; i < 10; i++) {
            long deadline = System.currentTimeMillis() + 300;
            try {
                int code = http.send(HttpRequest.newBuilder(URI.create("http://localhost:8203/")).timeout(Duration.ofMillis(300))
                        .header("X-Deadline", String.valueOf(deadline)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                if (code == 504) aFast504++;
            } catch (Exception e) { aTimeouts++; }
        }
        Thread.sleep(700);                                       // дать C доделать начатое
        System.out.printf("   %-21s A: timeouts %2d, fast 504 %2d; C: useless work done %2d, refused %2d%n",
                propagate ? "deadline propagated:" : "own timeout in B:", aTimeouts, aFast504, cDone.get(), cRefused.get());
        b.stop(0); c.stop(0);
    }

    static void call(String path) {
        try { http.send(HttpRequest.newBuilder(URI.create("http://localhost:8202" + path)).build(), HttpResponse.BodyHandlers.discarding()); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
