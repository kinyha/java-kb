import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class AsyncEventDemo {
    record Paid() {}

    static class Gate {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
    }

    static class Receipt {
        final Gate gate;
        Receipt(Gate gate) { this.gate = gate; }

        @Async("eventsExecutor")
        @EventListener
        public void send(Paid event) throws InterruptedException {
            // Удерживаем слушатель, чтобы порядок не зависел от скорости потоков.
            if (!gate.release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException();
            System.out.println("listener: " + Thread.currentThread().getName());
            gate.finished.countDown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync(proxyTargetClass = true)
    static class Config {
        @Bean Gate gate() { return new Gate(); }
        @Bean Receipt receipt(Gate gate) { return new Receipt(gate); }
        @Bean ThreadPoolTaskExecutor eventsExecutor() {
            var pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(10);
            pool.setThreadNamePrefix("events-");
            return pool; // Контейнер запускает и закрывает пул.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            var gate = ctx.getBean(Gate.class);
            ctx.publishEvent(new Paid());
            System.out.println("publisher returned");
            gate.release.countDown();
            if (!gate.finished.await(5, TimeUnit.SECONDS)) throw new IllegalStateException();
        }
    }
}
