import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.catalina.startup.Tomcat;

// Один экземпляр сервлета обслуживает все запросы, и его поле общее для них.
public class LifecycleDemo {
    static final Logger TOMCAT_LOG = Logger.getLogger("org.apache");

    static class GreetingServlet extends HttpServlet {
        static final AtomicInteger CREATED = new AtomicInteger();
        final int number = CREATED.incrementAndGet(); // Сколько экземпляров создал Tomcat.
        private String user; // Ошибка: поле общее для всех запросов.

        @Override
        public void init() {
            System.out.println("init() on " + Thread.currentThread().getName());
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            user = req.getParameter("user"); // alice записала себя в поле...
            sleep(300);                      // ...и ждёт банк, а bob за это время перезаписал поле.
            resp.getWriter().print("hello " + user + " (" + Thread.currentThread().getName()
                    + ", servlet #" + number + ")");
        }

        @Override
        public void destroy() {
            System.out.println("destroy() on " + Thread.currentThread().getName());
        }
    }

    public static void main(String[] args) throws Exception {
        TOMCAT_LOG.setLevel(Level.SEVERE); // Скрыть служебные сообщения Tomcat.
        var tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("tomcat").toString());
        tomcat.setPort(18080);
        tomcat.getConnector(); // Без этого вызова встроенный Tomcat не создаст HTTP-коннектор.
        var ctx = tomcat.addContext("", null);
        Tomcat.addServlet(ctx, "greeting", new GreetingServlet());
        ctx.addServletMapping("/hello", "greeting");
        tomcat.start();
        System.out.println("started, waiting for requests");

        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        var alice = client.sendAsync(get("alice"), BodyHandlers.ofString());
        sleep(100); // bob приходит, пока запрос alice ещё внутри doGet
        var bob = client.sendAsync(get("bob"), BodyHandlers.ofString());
        System.out.println("alice got: " + alice.get().body());
        System.out.println("bob got:   " + bob.get().body());

        tomcat.stop();
        tomcat.destroy();
    }

    static HttpRequest get(String user) {
        return HttpRequest.newBuilder(URI.create("http://localhost:18080/hello?user=" + user)).build();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
