package kb.data;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.cfg.Configuration;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/** Самостоятельные опыты для jpa-locking.html; каждый запуск получает новую H2. */
public class LockingDemo {
    @Entity(name = "Account") @Table(name = "account")
    public static class Account {
        @Id public Long id;
        public int balance;
        @Version public long version; // Версию меняет Hibernate, а не бизнес-код.
        public Account() {}
        Account(long id, int balance) { this.id = id; this.balance = balance; }
    }
    @Entity(name = "PlainAccount") @Table(name = "plain_account")
    public static class PlainAccount {
        @Id public Long id;
        public int balance;
        public PlainAccount() {}
        PlainAccount(long id, int balance) { this.id = id; this.balance = balance; }
    }
    @Entity(name = "Tariff") @Table(name = "tariff")
    @Cacheable @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    public static class Tariff {
        @Id public Long id;
        public int fee;
        @Version public long version;
        public Tariff() {}
        Tariff(long id, int fee) { this.id = id; this.fee = fee; }
    }
    @Entity(name = "Receipt") @Table(name = "receipt")
    public static class Receipt {
        @Id public Long id; // Ключ известен до INSERT: Hibernate может собрать batch.
        public int amount;
        public Receipt() {}
        Receipt(long id, int amount) { this.id = id; this.amount = amount; }
    }
    @Entity(name = "IdentityReceipt") @Table(name = "identity_receipt")
    public static class IdentityReceipt {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public int amount;
        public IdentityReceipt() {}
        IdentityReceipt(int amount) { this.amount = amount; }
    }

    static SessionFactory factory(String mode) {
        var config = new Configuration()
                .addAnnotatedClass(Account.class).addAnnotatedClass(PlainAccount.class)
                .addAnnotatedClass(Tariff.class).addAnnotatedClass(Receipt.class)
                .addAnnotatedClass(IdentityReceipt.class);
        config.setProperty("hibernate.connection.url", "jdbc:h2:mem:locking;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=1500");
        config.setProperty("hibernate.connection.username", "sa");
        config.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        config.setProperty("hibernate.generate_statistics", "true");
        config.setProperty("hibernate.jdbc.batch_size", mode.equals("batch-off") ? "0" : "3");
        config.setProperty("hibernate.cache.use_second_level_cache", mode.equals("l2") ? "true" : "false");
        config.setProperty("hibernate.cache.use_query_cache", "false");
        if (mode.equals("l2")) {
            config.setProperty("hibernate.cache.region.factory_class", "jcache");
            config.setProperty("hibernate.javax.cache.provider", "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");
            config.setProperty("hibernate.javax.cache.missing_cache_strategy", "create");
        }
        return config.buildSessionFactory();
    }

    static void seed(SessionFactory factory) {
        factory.inTransaction(session -> {
            session.persist(new Account(1, 100));
            session.persist(new PlainAccount(1, 100));
            session.persist(new Tariff(1, 100));
        });
        factory.getStatistics().clear();
    }

    static void lostUpdate(SessionFactory factory) {
        try (var first = factory.openSession(); var second = factory.openSession()) {
            first.beginTransaction(); second.beginTransaction();
            var a = first.find(PlainAccount.class, 1L);
            var b = second.find(PlainAccount.class, 1L); // Оба прочитали 100.
            a.balance -= 10; b.balance -= 20;
            first.getTransaction().commit();
            second.getTransaction().commit(); // Запишет 80 поверх сохранённых 90.
        }
        factory.inTransaction(session -> System.out.println("final balance=" + session.find(PlainAccount.class, 1L).balance));
    }

    static void optimistic(SessionFactory factory) {
        try (var first = factory.openSession(); var second = factory.openSession()) {
            first.beginTransaction(); second.beginTransaction();
            var a = first.find(Account.class, 1L);
            var b = second.find(Account.class, 1L);
            System.out.printf("loaded versions=%d,%d%n", a.version, b.version);
            a.balance -= 10; b.balance -= 20;
            first.getTransaction().commit();
            try { second.getTransaction().commit(); }
            catch (PersistenceException error) {
                System.out.println("second exception=" + error.getClass().getSimpleName());
                if (second.getTransaction().isActive()) second.getTransaction().rollback();
            }
        }
        factory.inTransaction(session -> {
            var saved = session.find(Account.class, 1L);
            System.out.printf("after conflict: balance=%d, version=%d%n", saved.balance, saved.version);
        });
        factory.inTransaction(session -> session.find(Account.class, 1L).balance -= 20); // Новая транзакция и свежее чтение.
        factory.inTransaction(session -> {
            var saved = session.find(Account.class, 1L);
            System.out.printf("after retry: balance=%d, version=%d%n", saved.balance, saved.version);
        });
    }

    static void pessimistic(SessionFactory factory) {
        try (var first = factory.openSession(); var second = factory.openSession()) {
            first.beginTransaction(); second.beginTransaction();
            var account = first.find(Account.class, 1L, LockModeType.PESSIMISTIC_WRITE);
            System.out.println("first locked balance=" + account.balance);
            try { second.find(Account.class, 1L, LockModeType.PESSIMISTIC_WRITE); }
            catch (PersistenceException error) {
                System.out.println("second exception=" + error.getClass().getSimpleName());
            } finally {
                if (second.getTransaction().isActive()) second.getTransaction().rollback();
            }
            account.balance -= 10;
            first.getTransaction().commit(); // Освободили блокировку.
        }
        factory.inTransaction(session -> {
            var account = session.find(Account.class, 1L, LockModeType.PESSIMISTIC_WRITE);
            System.out.println("after release: locked balance=" + account.balance);
            account.balance -= 20;
        });
        factory.inTransaction(session -> System.out.println("final balance=" + session.find(Account.class, 1L).balance));
    }

    static void firstLevel(SessionFactory factory) {
        factory.inTransaction(session -> {
            var first = session.find(Account.class, 1L);
            var same = session.find(Account.class, 1L);
            System.out.printf("same reference=%s, prepared statements=%d%n", first == same,
                    factory.getStatistics().getPrepareStatementCount());
            session.doWork(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("update account set balance=80 where id=1"); // SQL минует persistence context.
                }
            });
            System.out.println("after SQL: find balance=" + session.find(Account.class, 1L).balance);
            session.refresh(first); // Явно обновили managed-объект из базы.
            System.out.println("after refresh: balance=" + first.balance);
            session.clear(); // Удалили привязку объектов, не строки базы.
            var fresh = session.find(Account.class, 1L);
            System.out.printf("after clear: same reference=%s, balance=%d%n", first == fresh, fresh.balance);
        });
    }

    static Tariff tariff(SessionFactory factory) {
        try (var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            var result = session.find(Tariff.class, 1L);
            transaction.commit();
            return result; // Снаружи возвращаем detached-объект только для сравнения ссылок.
        }
    }

    static void secondLevel(SessionFactory factory) {
        factory.getCache().evictAllRegions(); // Прогреваем измерение с пустого L2.
        factory.getStatistics().clear();
        var first = tariff(factory);
        System.out.printf("first: SQL=%d, L2 misses=%d%n", factory.getStatistics().getPrepareStatementCount(),
                factory.getStatistics().getSecondLevelCacheMissCount());
        var second = tariff(factory);
        System.out.printf("second: same reference=%s, SQL=%d, L2 hits=%d%n", first == second,
                factory.getStatistics().getPrepareStatementCount(), factory.getStatistics().getSecondLevelCacheHitCount());
        long beforeQuery = factory.getStatistics().getPrepareStatementCount();
        for (int i = 0; i < 2; i++) factory.inTransaction(session ->
                session.createSelectionQuery("from Tariff", Tariff.class).getResultList());
        System.out.println("two JPQL queries: SQL=" + (factory.getStatistics().getPrepareStatementCount() - beforeQuery));
        factory.inTransaction(session -> session.doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("update tariff set fee=200 where id=1"); // Hibernate не знает об этой записи.
            }
        }));
        System.out.println("after external SQL: fee=" + tariff(factory).fee);
        factory.getCache().evictEntityData(Tariff.class, 1L);
        System.out.println("after eviction: fee=" + tariff(factory).fee);
    }

    static void batch(SessionFactory factory, boolean identity) {
        factory.getStatistics().clear();
        factory.inTransaction(session -> {
            for (int i = 1; i <= 6; i++) {
                if (identity) session.persist(new IdentityReceipt(10));
                else session.persist(new Receipt(i, 10));
                if (i % 3 == 0) {
                    System.out.println("before clear: managed=" + session.getStatistics().getEntityCount());
                    session.flush(); // Отправили накопленное, но не сделали commit.
                    session.clear(); // Освободили managed-объекты и их снимки.
                    System.out.println("after clear: managed=" + session.getStatistics().getEntityCount());
                }
            }
        });
        System.out.printf("inserted=%d, prepared statements=%d%n", factory.getStatistics().getEntityInsertCount(),
                factory.getStatistics().getPrepareStatementCount());
    }

    public static void main(String[] args) {
        System.setProperty("org.jboss.logging.provider", "jdk");
        LogManager.getLogManager().reset();
        Logger.getLogger("").setLevel(Level.OFF);
        String mode = args.length == 0 ? "optimistic" : args[0];
        try (var factory = factory(mode)) {
            seed(factory);
            switch (mode) {
                case "lost" -> lostUpdate(factory);
                case "optimistic" -> optimistic(factory);
                case "pessimistic" -> pessimistic(factory);
                case "l1" -> firstLevel(factory);
                case "l2" -> secondLevel(factory);
                case "batch", "batch-off" -> batch(factory, false);
                case "identity" -> batch(factory, true);
                default -> throw new IllegalArgumentException("Unknown mode: " + mode);
            }
        }
    }
}
