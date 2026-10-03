import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

// Отвечает именем пода, версией и конфигурацией: из переменной окружения и из смонтированного файла.
public class App {
    public static void main(String[] args) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(8080), 0);
        s.createContext("/", ex -> {
            Path f = Path.of("/config/greeting");
            String file = Files.exists(f) ? Files.readString(f).strip() : "-";
            String body = "pod=" + System.getenv("HOSTNAME") + " version=" + System.getenv("VERSION")
                    + " env=" + System.getenv("GREETING") + " file=" + file + "\n";
            ex.sendResponseHeaders(200, body.length());
            ex.getResponseBody().write(body.getBytes());
            ex.close();
        });
        s.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> s.stop(1)));
    }
}
