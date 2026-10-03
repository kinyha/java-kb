import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

// Мини-сервис: печатает, что видит JVM в контейнере, и корректно завершается по SIGTERM.
public class App {
    public static void main(String[] args) throws Exception {
        Runtime rt = Runtime.getRuntime();
        System.out.printf("cpus=%d maxHeap=%dMB%n", rt.availableProcessors(), rt.maxMemory() / 1024 / 1024);
        HttpServer s = HttpServer.create(new InetSocketAddress(8080), 0);
        s.createContext("/", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        s.start();
        rt.addShutdownHook(new Thread(() -> { System.out.println("SIGTERM: draining and stopping"); s.stop(1); }));
    }
}
