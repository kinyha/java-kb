import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

// Сервис под нагрузкой: 8 рабочих потоков, 20 мс на запрос — потолок около 400 запросов в секунду.
// POST /stall?ms=2000 — «пауза GC»: все обработчики стоят указанное время.
public class Service {
    static volatile long stallUntil;

    public static void main(String[] args) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(8601), 1000);
        s.setExecutor(Executors.newFixedThreadPool(8));
        s.createContext("/pay", ex -> {
            long wait = stallUntil - System.currentTimeMillis();
            try { if (wait > 0) Thread.sleep(wait); Thread.sleep(20); } catch (InterruptedException ignored) { }
            ex.sendResponseHeaders(200, -1); ex.close();
        });
        s.createContext("/stall", ex -> {
            stallUntil = System.currentTimeMillis() + Long.parseLong(ex.getRequestURI().getQuery().split("=")[1]);
            ex.sendResponseHeaders(204, -1); ex.close();
        });
        s.start();
        System.out.println("listening on 8601");
    }
}
