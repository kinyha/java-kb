package kb.testing;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentApp {
    // Сколько раз создавался полный контекст: читают тесты про кеш контекстов.
    public static final AtomicInteger CONTEXTS = new AtomicInteger();

    public static void main(String[] args) { SpringApplication.run(PaymentApp.class, args); }
}
