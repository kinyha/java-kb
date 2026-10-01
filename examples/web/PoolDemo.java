import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.AbstractProtocol;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;

// Соединения не занимают потоки, а медленные запросы занимают. Запуск: ./run.sh PoolDemo [virtual]
public class PoolDemo {
    static final Logger TOMCAT_LOG = Logger.getLogger("org.apache");
    static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    // Платёж синхронно ждёт ответа банка и всё это время держит поток Tomcat.
    static class PayServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            var bank = HttpRequest.newBuilder(URI.create("http://localhost:18081/charge")).build();
            try {
                CLIENT.send(bank, BodyHandlers.discarding()); // Поток стоит здесь, пока банк думает.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resp.getWriter().print(Thread.currentThread().getName());
        }
    }

    // Health-check ничего не ждёт, но ему тоже нужен поток.
    static class HealthServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.getWriter().print(Thread.currentThread().getName());
        }
    }

    record Result(double seconds, String thread) {}

    public static void main(String[] args) throws Exception {
        TOMCAT_LOG.setLevel(Level.SEVERE);
        // «Банк»: отвечает через 1 секунду и может обслуживать сколько угодно запросов параллельно.
        var bankServer = HttpServer.create(new InetSocketAddress(18081), 0);
        bankServer.createContext("/charge", exchange -> {
            sleep(1000);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        bankServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        bankServer.start();
        boolean virtual = args.length > 0 && args[0].equals("virtual");
        var tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("tomcat").toString());
        tomcat.setPort(18080);
        var protocol = (AbstractProtocol<?>) tomcat.getConnector().getProtocolHandler();
        if (virtual) {
            protocol.setExecutor(new VirtualThreadExecutor("tomcat-handler-")); // Так делает Boot при spring.threads.virtual.enabled=true.
        } else {
            protocol.setMaxThreads(2); // В Boot это server.tomcat.threads.max, по умолчанию 200.
        }
        var ctx = tomcat.addContext("", null);
        Tomcat.addServlet(ctx, "pay", new PayServlet());
        Tomcat.addServlet(ctx, "health", new HealthServlet());
        ctx.addServletMapping("/pay", "pay");
        ctx.addServletMapping("/health", "health");
        tomcat.start();
        System.out.println(virtual ? "mode: virtual threads" : "mode: maxThreads=2");

        // 50 клиентов подключились и молчат, как keep-alive соединения между запросами.
        List<Socket> idle = new ArrayList<>();
        for (int i = 0; i < 50; i++) idle.add(new Socket("localhost", 18080));
        System.out.printf("idle connections: %d, GET /health: %.1f s%n", idle.size(), call("/health").join().seconds());

        // Шесть платежей одновременно, через 0.1 с — health-check от балансировщика.
        List<CompletableFuture<Result>> payments = new ArrayList<>();
        for (int i = 0; i < 6; i++) payments.add(call("/pay"));
        sleep(100);
        var health = call("/health");

        var seconds = new ArrayList<String>();
        var threads = new TreeSet<String>();
        for (var p : payments) {
            var r = p.join();
            seconds.add(String.format("%.1f", r.seconds()));
            threads.add(r.thread());
        }
        seconds.sort(null);
        System.out.println("6 x /pay finished after: " + String.join(" ", seconds) + " s");
        System.out.println("threads used (" + threads.size() + "): " + String.join(", ", threads));
        System.out.printf("/health sent 0.1 s later: %.1f s%n", health.join().seconds());

        for (var s : idle) s.close();
        tomcat.stop();
        tomcat.destroy();
        bankServer.stop(0);
    }

    static CompletableFuture<Result> call(String path) {
        long start = System.nanoTime();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18080" + path)).build();
        return CLIENT.sendAsync(request, BodyHandlers.ofString()).thenApply(r -> {
            long tenths = (System.nanoTime() - start) / 100_000_000; // округление вниз до 0.1 с
            return new Result(tenths / 10.0, r.body());
        });
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
