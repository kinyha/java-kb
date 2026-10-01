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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

// Фильтры оборачивают сервлет в одном потоке; после коммита ответа заголовки уже не изменить.
public class FilterDemo {
    static final Logger TOMCAT_LOG = Logger.getLogger("org.apache");

    static class TimingFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            var resp = (HttpServletResponse) res;
            long start = System.nanoTime();
            System.out.println("  timing: before (" + Thread.currentThread().getName()
                    + ", client port " + req.getRemotePort() + ")"); // Порт клиента = одно и то же соединение.
            chain.doFilter(req, res); // Здесь выполняются следующий фильтр и сервлет.
            System.out.println("  timing: after, committed=" + resp.isCommitted());
            resp.setHeader("X-Elapsed-Ms", String.valueOf((System.nanoTime() - start) / 1_000_000)); // После коммита молча игнорируется.
        }
    }

    static class AuthFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            if (((HttpServletRequest) req).getHeader("X-Token") == null) {
                System.out.println("  auth: no token, chain stopped");
                ((HttpServletResponse) res).setStatus(401);
                return; // chain.doFilter не вызван: сервлет не узнает о запросе.
            }
            System.out.println("  auth: token ok");
            chain.doFilter(req, res);
            System.out.println("  auth: after");
        }
    }

    static class ReportServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            int size = Integer.parseInt(req.getParameter("size"));
            resp.getWriter().print("x".repeat(size)); // Буфер ответа 8 КБ: больше — Tomcat отправит заголовки клиенту.
            System.out.println("  servlet: wrote " + size + " chars, committed=" + resp.isCommitted());
        }
    }

    public static void main(String[] args) throws Exception {
        TOMCAT_LOG.setLevel(Level.SEVERE);
        var tomcat = new Tomcat();
        tomcat.setBaseDir(Files.createTempDirectory("tomcat").toString());
        tomcat.setPort(18080);
        tomcat.getConnector();
        var ctx = tomcat.addContext("", null);
        Tomcat.addServlet(ctx, "report", new ReportServlet());
        ctx.addServletMapping("/report", "report");
        addFilter(ctx, "timing", new TimingFilter()); // Порядок регистрации = порядок в цепочке.
        addFilter(ctx, "auth", new AuthFilter());
        tomcat.start();

        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        for (var c : new String[][] {{"100", "t"}, {"20000", "t"}, {"100", null}}) {
            System.out.println("GET /report?size=" + c[0] + (c[1] == null ? " without token" : ""));
            var request = HttpRequest.newBuilder(URI.create("http://localhost:18080/report?size=" + c[0]));
            if (c[1] != null) request.header("X-Token", c[1]);
            var resp = client.send(request.build(), BodyHandlers.ofString());
            System.out.println("  client: status " + resp.statusCode() + ", X-Elapsed-Ms "
                    + (resp.headers().firstValue("X-Elapsed-Ms").isPresent() ? "present" : "missing"));
        }
        tomcat.stop();
        tomcat.destroy();
    }

    static void addFilter(org.apache.catalina.Context ctx, String name, Filter filter) {
        var def = new FilterDef();
        def.setFilterName(name);
        def.setFilter(filter);
        ctx.addFilterDef(def);
        var map = new FilterMap();
        map.setFilterName(name);
        map.addURLPattern("/*");
        ctx.addFilterMap(map);
    }
}
