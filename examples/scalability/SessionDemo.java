import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

// Запуск: nginx с nginx.conf и Redis (см. README / run.sh), затем java -cp "$(cat target/classpath.txt)" SessionDemo.java
// Один и тот же сервис в двух экземплярах. Вход создаёт сессию, /me её читает.
public class SessionDemo {
    static final RedisClient redis = RedisClient.create("redis://localhost:6379");

    interface Store { void put(String sid, String user); String get(String sid); }

    public static void main(String[] args) throws Exception {
        // Сессии в памяти процесса: у каждого экземпляра своя карта.
        instance(8191, local()); instance(8192, local());
        // Сессии в Redis: общая для всех экземпляров.
        Store shared = new Store() {
            public void put(String sid, String user) { redis.set("session:" + sid, user, SetParams.setParams().ex(1800)); }
            public String get(String sid) { return redis.get("session:" + sid); }
        };
        instance(8193, shared); instance(8194, shared);

        System.out.println("== log in, then call /me 6 times through nginx round robin");
        client("sessions in memory", 8086);
        client("sessions in Redis", 8087);
        System.exit(0);
    }

    static Store local() {
        Map<String, String> m = new ConcurrentHashMap<>();
        return new Store() {
            public void put(String sid, String user) { m.put(sid, user); }
            public String get(String sid) { return m.get(sid); }
        };
    }

    static void instance(int port, Store store) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 50);
        s.createContext("/login", ex -> {
            String sid = UUID.randomUUID().toString();
            store.put(sid, "anna");
            ex.getResponseHeaders().add("Set-Cookie", "SID=" + sid);
            reply(ex, 200, "logged in on " + port);
        });
        s.createContext("/me", ex -> {
            String cookie = ex.getRequestHeaders().getFirst("Cookie");
            String user = cookie == null ? null : store.get(cookie.replace("SID=", ""));
            reply(ex, user == null ? 401 : 200, (user == null ? "who are you?" : user) + " @" + port);
        });
        s.start();
    }

    static void reply(HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes();
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    static void client(String name, int port) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        var login = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).build(), HttpResponse.BodyHandlers.ofString());
        String cookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        StringBuilder sb = new StringBuilder();
        int ok = 0;
        for (int i = 0; i < 6; i++) {
            var r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me")).header("Cookie", cookie).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) ok++;
            sb.append(r.statusCode()).append(' ');
        }
        System.out.printf("   %-20s %s; /me: %s-> %d of 6 recognized%n", name, login.body(), sb, ok);
    }
}
