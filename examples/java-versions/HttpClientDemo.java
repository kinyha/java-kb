import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class HttpClientDemo {
    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bank", exchange -> { // fixture always responds with a real HTTP 500
            byte[] body = "bank unavailable".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
            finally { exchange.close(); }
        });
        server.start();
        try (var client = HttpClient.newHttpClient()) { // close/AutoCloseable is available since Java 21
            var request = HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/bank"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            var sync = client.send(request, HttpResponse.BodyHandlers.ofString());
            var async = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).join();
            if (sync.statusCode() != 500 || async.statusCode() != 500
                    || !sync.body().equals("bank unavailable")) throw new AssertionError();
            System.out.println("sync=" + sync.statusCode() + "; async=" + async.statusCode()
                    + "; body=" + sync.body()); // HTTP error status is still a successfully received response
        } finally { server.stop(0); }
    }
}
