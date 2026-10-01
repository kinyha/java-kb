import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.*;
import org.springframework.transaction.support.*;

public class TransactionEventDemo {
    record Paid() {}

    static class Listeners {
        @EventListener
        public void ordinary(Paid event) { System.out.println("ordinary"); }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void before(Paid event) { System.out.println("before commit"); }

        @TransactionalEventListener @Order(1)
        public void committed(Paid event) { System.out.println("after commit"); }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
        @Order(1)
        public void rolledBack(Paid event) { System.out.println("after rollback"); }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
        @Order(2)
        public void completed(Paid event) { System.out.println("completion"); }
    }

    // Учебный менеджер без БД. Синхронизации и фазы исполняет настоящий Spring.
    static class DemoTxManager extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object tx, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {
            System.out.println("manager commit");
        }
        protected void doRollback(DefaultTransactionStatus status) {
            System.out.println("manager rollback");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean Listeners listeners() { return new Listeners(); }
        @Bean PlatformTransactionManager transactionManager() {
            return new DemoTxManager();
        }
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            var tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
            System.out.println("outside transaction:");
            ctx.publishEvent(new Paid()); // fallbackExecution оставлен false.

            System.out.println("commit:");
            tx.executeWithoutResult(status -> {
                ctx.publishEvent(new Paid());
                System.out.println("publication returned");
            });

            System.out.println("rollback:");
            tx.executeWithoutResult(status -> {
                ctx.publishEvent(new Paid());
                status.setRollbackOnly();
                System.out.println("publication returned");
            });
        }
    }
}
