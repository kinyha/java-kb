import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class LifecycleDemo {
    static class Gateway implements BeanNameAware, InitializingBean, DisposableBean {
        @Autowired Clock clock;
        boolean ready;
        Gateway() { System.out.println("constructor: clock=" + (clock != null)); }
        public void setBeanName(String name) {
            System.out.println("aware: " + name + ", clock=" + (clock != null));
        }
        @PostConstruct void annotatedInit() { System.out.println("postConstruct"); }
        public void afterPropertiesSet() { System.out.println("afterPropertiesSet"); }
        void warmup() { ready = true; System.out.println("warmup"); }
        @PreDestroy void annotatedDestroy() { System.out.println("preDestroy"); }
        public void destroy() { System.out.println("destroy"); }
        void disconnect() { System.out.println("disconnect"); }
    }
    static class Trace implements BeanPostProcessor {
        public Object postProcessBeforeInitialization(Object bean, String name) {
            if (bean instanceof Gateway) System.out.println("before init");
            return bean;
        }
        public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof Gateway) System.out.println("after init");
            return bean; // Другой обработчик здесь мог бы вернуть прокси.
        }
    }
    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean("clock", Clock.class, Clock::systemUTC);
            ctx.registerBean("gateway", Gateway.class);
            ctx.addBeanFactoryPostProcessor(factory -> {
                var definition = factory.getBeanDefinition("gateway");
                definition.setInitMethodName("warmup");
                definition.setDestroyMethodName("disconnect");
                System.out.println("definitions changed"); // Объекта Gateway ещё нет.
            });
            // Добавлен вручную раньше обработчика аннотаций PostConstruct.
            ctx.getBeanFactory().addBeanPostProcessor(new Trace());
            ctx.refresh();
            System.out.println("usable: " + ctx.getBean(Gateway.class).ready);
        } // Закрытие вызывает три разных способа завершения Gateway.
    }
}
