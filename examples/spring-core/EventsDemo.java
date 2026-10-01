import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

public class EventsDemo {
    record Paid(boolean fail) {}

    static class Listeners {
        @EventListener @Order(1)
        public void audit(Paid event) {
            System.out.println("audit: " + Thread.currentThread().getName());
        }

        @EventListener @Order(2)
        public void receipt(Paid event) {
            System.out.println("receipt");
            if (event.fail()) throw new IllegalStateException("receipt failed");
        }

        @EventListener @Order(3)
        public void statistics(Paid event) {
            System.out.println("statistics");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean Listeners listeners() { return new Listeners(); }
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            System.out.println("success:");
            ctx.publishEvent(new Paid(false));
            System.out.println("caller returned");
            System.out.println("failure:");
            try {
                ctx.publishEvent(new Paid(true));
            } catch (IllegalStateException e) {
                // Ошибка второго слушателя прерывает рассылку и доходит до издателя.
                System.out.println("caller caught: " + e.getClass().getSimpleName());
            }
        }
    }
}
