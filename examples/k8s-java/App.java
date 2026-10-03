import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

// /        — обычный запрос (20 мс)
// /live    — процесс жив
// /ready   — готов принимать трафик: «база» (сервис db:5432) отвечает за 300 мс
// ALLOC_MB — сколько памяти занять при старте (для опыта с лимитом)
public class App {
    static final List<byte[]> hold = new ArrayList<>();

    static boolean dbUp() {
        try (var sock = new java.net.Socket()) { sock.connect(new InetSocketAddress("db", 5432), 300); return true; }
        catch (Exception e) { return false; }
    }

    public static void main(String[] args) throws Exception {
        long maxMb = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        System.out.println("maxHeap=" + maxMb + "MB");
        int alloc = Integer.parseInt(System.getenv().getOrDefault("ALLOC_MB", "0"));
        for (int i = 0; i < alloc; i++) { hold.add(new byte[1024 * 1024]); if (i % 50 == 49) System.out.println("allocated " + (i + 1) + "MB"); }

        HttpServer s = HttpServer.create(new InetSocketAddress(8080), 100);
        s.setExecutor(Executors.newFixedThreadPool(8));
        s.createContext("/", ex -> {
            try { Thread.sleep(20); } catch (InterruptedException ignored) { }
            String body = System.getenv("HOSTNAME") + "\n";
            ex.sendResponseHeaders(200, body.length()); ex.getResponseBody().write(body.getBytes()); ex.close();
        });
        s.createContext("/live", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        s.createContext("/ready", ex -> { ex.sendResponseHeaders(dbUp() ? 200 : 503, -1); ex.close(); });
        s.start();
        // SIGTERM: дождаться завершения начатых запросов (до 5 с) и выйти.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { System.out.println("SIGTERM"); s.stop(5); }));
    }
}
