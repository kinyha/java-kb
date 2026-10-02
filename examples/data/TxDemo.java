package kb.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Полные варианты для topics/spring-tx.html. Все обращения к БД идут через JdbcTemplate. */
public class TxDemo {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") HikariDataSource dataSource() {
            var settings = new HikariConfig();
            settings.setJdbcUrl("jdbc:h2:mem:tx;DB_CLOSE_DELAY=-1");
            settings.setMaximumPoolSize(4); // REQUIRES_NEW получает второе подключение.
            return new HikariDataSource(settings);
        }
        @Bean JdbcTemplate jdbc(HikariDataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(HikariDataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }
        @Bean Steps steps(JdbcTemplate jdbc) { return new Steps(jdbc); }
        @Bean PaymentService service(JdbcTemplate jdbc, Steps steps) { return new PaymentService(jdbc, steps); }
    }

    static class BankException extends Exception {}

    public static class Steps {
        private final JdbcTemplate jdbc;
        Steps(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional
        public void requiredFailure() {
            jdbc.update("insert into audit values ('required')");
            throw new IllegalStateException("audit rejected");
        }
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void independentAudit() { jdbc.update("insert into audit values ('new')"); }

        @Transactional(propagation = Propagation.NESTED)
        public void nestedFailure() {
            jdbc.update("insert into audit values ('nested')");
            throw new IllegalStateException("audit rejected");
        }
        @Transactional(propagation = Propagation.NESTED)
        public void nestedSuccess() { jdbc.update("insert into audit values ('nested')"); }
    }

    public static class PaymentService {
        private final JdbcTemplate jdbc;
        private final Steps steps;
        PaymentService(JdbcTemplate jdbc, Steps steps) { this.jdbc = jdbc; this.steps = steps; }
        void record() {
            jdbc.update("update account set balance = balance - 10 where id = 1");
            jdbc.update("insert into payment values ('p-17')");
        }

        @Transactional
        public void fail() {
            System.out.println("transaction active=" + TransactionSynchronizationManager.isActualTransactionActive());
            record();
            throw new IllegalStateException("bank rejected");
        }
        public void selfInvocation() { fail(); } // this.fail() обходит прокси.

        @Transactional
        public void checkedFailure() throws BankException { record(); throw new BankException(); }

        @Transactional(rollbackFor = BankException.class)
        public void checkedRollback() throws BankException { record(); throw new BankException(); }

        @Transactional
        public void swallowed() {
            record();
            try { throw new IllegalStateException("bank rejected"); }
            catch (IllegalStateException ignored) { /* Прокси увидит обычный возврат. */ }
        }

        @Transactional
        public void required() {
            record();
            try { steps.requiredFailure(); } // Вторая логическая граница, та же транзакция БД.
            catch (IllegalStateException ignored) { /* rollback-only уже установлен. */ }
        }

        @Transactional
        public void requiresNew() {
            record();
            steps.independentAudit(); // Отдельный бин, поэтому вызов проходит через его прокси.
            throw new IllegalStateException("payment rejected");
        }

        @Transactional
        public void nested() {
            record();
            try { steps.nestedFailure(); }
            catch (IllegalStateException ignored) { /* Откат до savepoint; внешний платёж продолжается. */ }
        }

        @Transactional
        public void nestedOuterFailure() {
            record();
            steps.nestedSuccess();
            throw new IllegalStateException("payment rejected"); // Откатит и вложенную запись.
        }

        @Transactional(readOnly = true)
        public void readOnlyWrite() {
            System.out.println("readOnly flag=" + TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            record(); // H2 не запрещает запись по JDBC hint setReadOnly(true).
        }

        @Transactional
        public void otherThread() throws Exception {
            System.out.println("caller transaction=" + TransactionSynchronizationManager.isActualTransactionActive());
            var result = new CompletableFuture<Boolean>();
            Thread.ofVirtual().start(() -> result.complete(TransactionSynchronizationManager.isActualTransactionActive()));
            System.out.println("worker transaction=" + result.get(10, TimeUnit.SECONDS)); // Новый поток не наследует ресурсы транзакции.
        }
    }

    interface Operation { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("create table account(id int primary key, balance int)");
            jdbc.execute("create table payment(id varchar(40))");
            jdbc.execute("create table audit(kind varchar(40))");
            jdbc.update("insert into account values (1, 100)");
            var service = context.getBean(PaymentService.class);
            String mode = args.length == 0 ? "proxy" : args[0];
            Operation operation = switch (mode) {
                case "proxy" -> service::fail;
                case "self" -> service::selfInvocation;
                case "checked" -> service::checkedFailure;
                case "checked-rollback" -> service::checkedRollback;
                case "swallowed" -> service::swallowed;
                case "required" -> service::required;
                case "new" -> service::requiresNew;
                case "nested" -> service::nested;
                case "nested-outer" -> service::nestedOuterFailure;
                case "readonly" -> service::readOnlyWrite;
                case "thread" -> service::otherThread;
                case "template" -> () -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                        .executeWithoutResult(status -> {
                            jdbc.update("insert into payment values ('p-17')");
                            status.setRollbackOnly(); // Обычный возврат, но явное решение об откате.
                        });
                default -> throw new IllegalArgumentException("Unknown mode: " + mode);
            };
            try { operation.run(); }
            catch (Exception error) { System.out.println("exception=" + error.getClass().getSimpleName()); }
            System.out.printf("balance=%d, payments=%d, audit=%d%n",
                    jdbc.queryForObject("select balance from account where id=1", Integer.class),
                    jdbc.queryForObject("select count(*) from payment", Integer.class),
                    jdbc.queryForObject("select count(*) from audit", Integer.class));
        }
    }
}
