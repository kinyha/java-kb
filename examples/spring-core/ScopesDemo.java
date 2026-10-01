import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;

public class ScopesDemo {
    static int created, initialized, destroyed;
    static class Draft {
        final int id = ++created;
        @PostConstruct void init() { initialized++; }
        @PreDestroy void destroy() { destroyed++; }
    }
    record Service(Draft fixed, ObjectProvider<Draft> fresh) {}
    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean @Scope("prototype") Draft draft() { return new Draft(); }
        @Bean Service service(Draft draft, ObjectProvider<Draft> provider) {
            return new Service(draft, provider); // Прямая ссылка выбирается один раз.
        }
    }
    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            Service s = ctx.getBean(Service.class);
            System.out.println("fixed: " + s.fixed().id + " " + s.fixed().id);
            Draft first = s.fresh().getObject(); // Новый запрос к фабрике.
            Draft second = s.fresh().getObject();
            System.out.println("provider: " + first.id + " " + second.id);
            System.out.println("initialized: " + initialized);
        } // Закрытие контекста не уничтожает выданные prototype.
        System.out.println("destroyed after close: " + destroyed);
    }
}
