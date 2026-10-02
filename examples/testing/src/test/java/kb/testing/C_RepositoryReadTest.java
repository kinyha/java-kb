package kb.testing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(Containers.class) // Та же конфигурация, что у B: контекст берётся из кеша.
class C_RepositoryReadTest {
    @Autowired PaymentRepository repository;

    @Test
    void seesNothingAfterRollback() {
        System.out.println("DEMO C: count=" + repository.count() + ", contexts=" + PaymentApp.CONTEXTS.get());
    }
}
