package kb.testing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(Containers.class)
@Transactional // Тестовая транзакция откатывается после каждого метода.
class B_RepositoryWriteTest {
    @Autowired PaymentRepository repository;
    @Autowired org.springframework.context.ApplicationContext ctx;

    @Test
    void savesPayment() {
        repository.save("acc-1", 500, "APPROVED");
        System.out.println("DEMO B: full context beans=" + ctx.getBeanDefinitionCount());
        System.out.println("DEMO B: count inside test=" + repository.count() + ", contexts=" + PaymentApp.CONTEXTS.get());
        assertThat(repository.count()).isEqualTo(1);
    }
}
