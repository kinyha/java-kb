import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.*;

public class BeansDemo {
    static int created, initialized;
    static class Gateway {
        // Считаем все new, включая объекты вне контейнера.
        Gateway() { created++; }
        // Этот метод вызывает Spring при подготовке бина.
        @PostConstruct void init() { initialized++; }
    }
    record Service(Gateway gateway) {}
    @Configuration
    static class Full {
        @Bean Gateway gateway() { return new Gateway(); }
        @Bean Service service() {
            // Spring перенаправит gateway() в контейнер.
            return new Service(gateway());
        }
    }
    @Configuration(proxyBeanMethods = false)
    static class Lite {
        @Bean Gateway gateway() { return new Gateway(); }
        @Bean Service service() {
            // Прямой вызов снова выполнит new Gateway().
            return new Service(gateway());
        }
    }
    @Configuration(proxyBeanMethods = false)
    static class Parameters {
        @Bean Gateway gateway() { return new Gateway(); }
        @Bean Service service(Gateway gateway) {
            // Готовую зависимость передаёт Spring.
            return new Service(gateway);
        }
    }
    record Token(int number) {}
    static class TokenFactory implements FactoryBean<Token> {
        int number;
        public Token getObject() { return new Token(++number); }
        // Тип продукта доступен без его создания.
        public Class<?> getObjectType() { return Token.class; }
        // Spring сохраняет продукт отдельно от экземпляра фабрики.
        public boolean isSingleton() { return true; }
    }
    @Configuration(proxyBeanMethods = false)
    static class Factories {
        @Bean TokenFactory token() { return new TokenFactory(); }
    }
    static void compare(Class<?> config) {
        created = initialized = 0;
        try (var ctx = new AnnotationConfigApplicationContext(config)) {
            Gateway inService = ctx.getBean(Service.class).gateway();
            Gateway inContext = ctx.getBean(Gateway.class);
            System.out.printf("%s: same=%s, created=%d, initialized=%d%n",
                config.getSimpleName(), inService == inContext, created, initialized);
        }
    }
    public static void main(String[] args) {
        compare(Full.class); compare(Lite.class); compare(Parameters.class);
        try (var ctx = new AnnotationConfigApplicationContext(Factories.class)) {
            Object first = ctx.getBean("token"); // Получаем продукт.
            System.out.println("product same: " + (first == ctx.getBean("token")));
            Object factory = ctx.getBean("&token"); // Получаем саму фабрику.
            System.out.println("factory: " + factory.getClass().getSimpleName());
        }
    }
}
