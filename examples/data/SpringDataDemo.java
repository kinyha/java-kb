package kb.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

public class SpringDataDemo {
    @Entity(name = "Payment")
    @Table(name = "repository_payment")
    public static class Payment {
        @Id String id; // Идентификатор назначает приложение.
        @Version Long version; // null позволяет Spring Data считать объект новым.
        String status;
        long cents;
        protected Payment() {}
        Payment(String id, String status, long cents) { this.id = id; this.status = status; this.cents = cents; }
        public String getId() { return id; }
        public Long getVersion() { return version; }
        public String getStatus() { return status; }
        public long getCents() { return cents; }
    }

    public interface PaymentView {
        String getId();
        long getCents(); // Закрытая проекция: известен набор нужных свойств.
    }

    public interface Payments extends JpaRepository<Payment, String> {
        List<Payment> findByStatusAndCentsGreaterThanEqualOrderByIdAsc(String status, long minimum);
        @Query("select p from Payment p where p.cents >= :minimum order by p.id")
        List<Payment> expensive(@Param("minimum") long minimum); // JPQL: поля Java, не столбцы SQL.
        List<PaymentView> findByStatusOrderByIdAsc(String status);
        Page<Payment> findByStatus(String status, Pageable pageable);
        Slice<Payment> findSliceByStatus(String status, Pageable pageable);
    }

    public static class Selects implements StatementInspector {
        static final AtomicInteger count = new AtomicInteger();
        @Override public String inspect(String sql) {
            if (sql.stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("select")) count.incrementAndGet();
            return sql;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = SpringDataDemo.class, considerNestedRepositories = true)
    static class Config {
        @Bean(destroyMethod = "close") HikariDataSource dataSource() {
            var settings = new HikariConfig();
            settings.setJdbcUrl("jdbc:h2:mem:repositories;DB_CLOSE_DELAY=-1");
            settings.setMaximumPoolSize(2);
            return new HikariDataSource(settings);
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(HikariDataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("kb.data"); // В target этого демо только его собственные классы.
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.cache.use_second_level_cache", "false",
                    "hibernate.session_factory.statement_inspector", new Selects()));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Payments repository = context.getBean(Payments.class);
            var original = new Payment("p-17", "NEW", 1_000);
            var inserted = repository.save(original); // Своя транзакция save завершается до возврата.
            System.out.println("new save same object=" + (inserted == original));
            original.cents = 1_200; // После возврата внешней транзакции нет: объект detached.
            System.out.println("without save cents=" + repository.findById("p-17").orElseThrow().cents);
            var merged = repository.save(original);
            System.out.println("update save same object=" + (merged == original));
            System.out.println("after save cents=" + repository.findById("p-17").orElseThrow().cents);
            repository.save(new Payment("p-18", "NEW", 2_000));
            repository.save(new Payment("p-19", "NEW", 3_000));
            repository.save(new Payment("p-20", "PAID", 4_000));
            System.out.println("derived=" + ids(repository.findByStatusAndCentsGreaterThanEqualOrderByIdAsc("NEW", 2_000)));
            System.out.println("jpql=" + ids(repository.expensive(3_000)));
            var views = repository.findByStatusOrderByIdAsc("NEW");
            System.out.println("projection=" + views.stream().map(v -> v.getId() + ":" + v.getCents()).toList());
            var request = PageRequest.of(0, 2, Sort.by("id")); // Детерминированный порядок по уникальному id.
            Selects.count.set(0);
            var page = repository.findByStatus("NEW", request);
            System.out.println("page=" + ids(page.getContent()) + ", total=" + page.getTotalElements()
                    + ", selects=" + Selects.count.get());
            Selects.count.set(0);
            var slice = repository.findSliceByStatus("NEW", request);
            System.out.println("slice=" + ids(slice.getContent()) + ", hasNext=" + slice.hasNext()
                    + ", selects=" + Selects.count.get());
        }
    }

    static List<String> ids(List<Payment> payments) { return payments.stream().map(p -> p.id).toList(); }
}
