import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.request.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class RequestScopeDemo {
    public static class Draft {
        static int created;
        final int id = ++created;
        public int id() { return id; } // Читаем поле через метод реального объекта.
    }
    record Service(Draft draft) {
        int currentId() { return draft.id(); }
    }
    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean @RequestScope Draft draft() { return new Draft(); }
        @Bean Service service(Draft draft) {
            return new Service(draft); // В singleton передаётся долгоживущий прокси.
        }
    }
    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigWebApplicationContext()) {
            ctx.setServletContext(new MockServletContext());
            ctx.register(Config.class);
            ctx.refresh(); // Реального HTTP-запроса пока нет, сервис уже создан.
            var s = ctx.getBean(Service.class);
            for (int request = 1; request <= 2; request++) {
                var attrs = new ServletRequestAttributes(new MockHttpServletRequest());
                RequestContextHolder.setRequestAttributes(attrs);
                try {
                    System.out.println("request " + request + ": " +
                        s.currentId() + " " + s.currentId());
                } finally {
                    attrs.requestCompleted(); // Завершение времени жизни запроса.
                    RequestContextHolder.resetRequestAttributes();
                }
            }
            try { s.currentId(); } // Получение target требует активного запроса.
            catch (org.springframework.beans.factory.support.ScopeNotActiveException ex) {
                System.out.println("outside request: " + ex.getClass().getSimpleName());
            }
        }
    }
}
