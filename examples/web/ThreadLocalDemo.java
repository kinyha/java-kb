import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.AbstractProtocol;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

// Поток из пула переживает запрос, а вместе с ним и значение ThreadLocal.
public class ThreadLocalDemo {
    static final Logger TOMCAT_LOG = Logger.getLogger("org.apache");
    static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>(); // Как MDC или SecurityContextHolder.

    static class LeakyUserFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            String user = ((HttpServletRequest) req).getHeader("X-User");
            if (user != null) CURRENT_USER.set(user); // Ошибка: нет заголовка — остаётся значение прошлого запроса.
            chain.doFilter(req, res);                // Ошибка: после запроса значение не удалено.
        }
    }

    static class SafeUserFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            CURRENT_USER.set(((HttpServletRequest) req).getHeader("X-User")); // null тоже записываем.
            try {
                chain.doFilter(req, res);
            } finally {
                CURRENT_USER.remove(); // Поток вернётся в пул чистым, даже если сервлет бросил исключение.
            }
        }
    }

    static class WhoAmIServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.getWriter().print("user=" + Objects.requireNonNullElse(CURRENT_USER.get(), "anonymous")
                    + " on " + Thread.currentThread().getName());
        }
    }

    public static void main(String[] args) throws Exception {
        TOMCAT_LOG.setLevel(Level.SEVERE);
        var tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("tomcat").toString());
        tomcat.setPort(18080);
        ((AbstractProtocol<?>) tomcat.getConnector().getProtocolHandler()).setMaxThreads(1); // Один поток, чтобы повтор был гарантирован.
        var ctx = tomcat.addContext("", null);
        Tomcat.addServlet(ctx, "whoami", new WhoAmIServlet());
        ctx.addServletMapping("/leaky/me", "whoami");
        ctx.addServletMapping("/safe/me", "whoami");
        addFilter(ctx, "leaky", new LeakyUserFilter(), "/leaky/*");
        addFilter(ctx, "safe", new SafeUserFilter(), "/safe/*");
        tomcat.start();

        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        for (var path : new String[] {"/leaky/me", "/safe/me"}) {
            System.out.println(path);
            System.out.println("  alice:     " + get(client, path, "alice"));
            System.out.println("  no header: " + get(client, path, null));
        }
        tomcat.stop();
        tomcat.destroy();
    }

    static String get(HttpClient client, String path, String user) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:18080" + path));
        if (user != null) request.header("X-User", user);
        return client.send(request.build(), BodyHandlers.ofString()).body();
    }

    static void addFilter(Context ctx, String name, Filter filter, String pattern) {
        var def = new FilterDef();
        def.setFilterName(name);
        def.setFilter(filter);
        ctx.addFilterDef(def);
        var map = new FilterMap();
        map.setFilterName(name);
        map.addURLPattern(pattern);
        ctx.addFilterMap(map);
    }
}
