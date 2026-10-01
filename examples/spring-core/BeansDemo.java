import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.*;

public class BeansDemo {
    static int created, initialized;
    static class Gateway {
        Gateway() { created++; } // Считаем все созданные объекты, включая лишние.
        @PostConstruct void init() { initialized++; } // Считает объекты, подготовленные Spring.
    }
    record Service(Gateway gateway) {}
    @Configuration
    static class Full {
        @Bean Gateway gateway() { return new Gateway(); }
        @Bean Service service() { return new Service(gateway()); } // Spring перенаправит gateway() в контейнер.
    }
    @Configuration(proxyBeanMethods = false)
    static class Lite {
        @Bean Gateway gateway() { return new Gateway(); }
        @Bean Service service() { return new Service(gateway()); } // Обычный вызов создаёт лишний объект.
    }
    @Configuration(proxyBeanMethods = false)
    static class Parameters {
        @Bean Gateway gateway() { return new Gateway(); }
        @Bean Service service(Gateway gateway) { return new Service(gateway); } // Готовую зависимость передаёт Spring.
    }
    record Token(int number) {}
    static class TokenFactory implements FactoryBean<Token> {
        int number;
        public Token getObject() { return new Token(++number); }
        public Class<?> getObjectType() { return Token.class; } // Тип продукта доступен без его создания.
        public boolean isSingleton() { return true; } // Spring сохраняет продукт отдельно от экземпляра фабрики.
    }
    @Configuration(proxyBeanMethods = false)
    static class Factories {
        @Bean TokenFactory token() { return new TokenFactory(); }
    }
    static void compare(Class<?> config) {
        created = initialized = 0;
        try (var ctx = new AnnotationConfigApplicationContext(config)) {
            System.out.printf("%s: same=%s, created=%d, initialized=%d%n",
                config.getSimpleName(), ctx.getBean(Service.class).gateway() == ctx.getBean(Gateway.class),
                created, initialized);
        }
    }
    public static void main(String[] args) {
        compare(Full.class); compare(Lite.class); compare(Parameters.class);
        try (var ctx = new AnnotationConfigApplicationContext(Factories.class)) {
            System.out.println("product same: " + (ctx.getBean("token") == ctx.getBean("token")));
            System.out.println("factory: " + ctx.getBean("&token").getClass().getSimpleName());
        }
    }
}
