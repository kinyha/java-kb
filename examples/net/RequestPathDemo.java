import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Запуск: java RequestPathDemo.java   (JDK 21, только loopback, сеть не нужна)
// Один порт, один процесс: «обратный прокси» выбирает backend по заголовку Host.
public class RequestPathDemo {

    public static void main(String[] args) throws Exception {
        try (var server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int port = server.getLocalPort();
            Thread proxy = Thread.ofVirtual().start(() -> serve(server));

            for (String host : List.of("pay.example.test", "admin.example.test")) {
                try (var socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
                    // Четвёрка, которая отличает это соединение от всех остальных.
                    System.out.println("connection: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort()
                            + " -> " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                    String request = "GET /payments/17 HTTP/1.1\r\n"
                            + "Host: " + host + "\r\n"            // Единственное отличие запросов.
                            + "Connection: close\r\n"
                            + "\r\n";                              // Пустая строка — конец заголовков.
                    socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                    System.out.println("request bytes: " + request.length()
                            + ", first line: " + request.lines().findFirst().orElseThrow());
                    var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String status = in.readLine();
                    String line, body = "";
                    while ((line = in.readLine()) != null) body = line; // Последняя строка — тело.
                    System.out.println("response: " + status + " | " + body);
                }
            }
            proxy.interrupt();
        }
    }

    // Читает заголовки, по Host выбирает «backend» и отвечает.
    static void serve(ServerSocket server) {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                var in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                List<String> headers = new ArrayList<>();
                for (String l; (l = in.readLine()) != null && !l.isEmpty(); ) headers.add(l);
                String host = headers.stream().filter(h -> h.regionMatches(true, 0, "Host:", 0, 5))
                        .map(h -> h.substring(5).trim()).findFirst().orElse("");
                String backend = host.startsWith("admin.") ? "admin-backend" : "payments-backend";
                String body = backend + " served " + headers.get(0).split(" ")[1]
                        + " for client port " + s.getPort();
                String response = "HTTP/1.1 200 OK\r\nContent-Length: " + (body.length() + 1)
                        + "\r\nConnection: close\r\n\r\n" + body + "\n";
                OutputStream out = s.getOutputStream();
                out.write(response.getBytes(StandardCharsets.US_ASCII));
            } catch (Exception e) {
                return;
            }
        }
    }
}
