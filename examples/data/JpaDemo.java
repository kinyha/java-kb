package kb.data;

import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Один объект, четыре границы: контекст, flush, merge и массовый UPDATE. */
public class JpaDemo {
    @Entity(name = "Payment")
    @Table(name = "payments")
    public static class Payment {
        @Id Long id;
        @Column(nullable = false, unique = true) String merchantRef;
        @Column(nullable = false) long amount;
        @Enumerated(EnumType.STRING) @Column(nullable = false) Status status;
        protected Payment() { }
        Payment(Long id, long amount) {
            this.id = id; this.amount = amount;
            this.merchantRef = "shop-" + id; this.status = Status.NEW;
        }
    }
    enum Status { NEW, PAID }
    static class SqlLog implements StatementInspector {
        final List<String> sql = new ArrayList<>();
        @Override public String inspect(String statement) { sql.add(statement); return statement; }
        long updates() { return sql.stream().filter(s -> s.toLowerCase().startsWith("update ")).count(); }
        void clear() { sql.clear(); }
    }
    public static void main(String[] args) {
        System.setProperty("org.jboss.logging.provider", "slf4j");
        SqlLog sql = new SqlLog();
        Configuration cfg = new Configuration().addAnnotatedClass(Payment.class);
        cfg.setProperty("hibernate.connection.url", "jdbc:h2:mem:jpa;DB_CLOSE_DELAY=-1");
        cfg.setProperty("hibernate.connection.username", "sa");
        cfg.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        cfg.setProperty("hibernate.cache.use_second_level_cache", "false");
        cfg.setStatementInspector(sql);
        try (SessionFactory factory = cfg.buildSessionFactory()) {
            seed(factory);
            String mode = args.length == 0 ? "dirty" : args[0];
            switch (mode) {
                case "dirty" -> dirty(factory, sql);
                case "merge" -> merge(factory);
                case "query" -> query(factory, sql);
                case "bulk" -> bulk(factory);
                default -> throw new IllegalArgumentException("dirty | merge | query | bulk");
            }
        }
    }
    static void seed(SessionFactory factory) { tx(factory, em -> em.persist(new Payment(17L, 100))); }
    static void tx(SessionFactory factory, Consumer<EntityManager> work) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            try { work.accept(em); em.getTransaction().commit(); }
            catch (RuntimeException e) { if (em.getTransaction().isActive()) em.getTransaction().rollback(); throw e; }
        }
    }
    static long databaseAmount(EntityManager em) {
        // JDBC на том же соединении читает строку, обходя память контекста и AUTO flush запроса JPA.
        return em.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.prepareStatement("select amount from payments where id = 17");
                 ResultSet row = statement.executeQuery()) { row.next(); return row.getLong(1); }
        });
    }
    static long savedAmount(SessionFactory factory) {
        try (EntityManager em = factory.createEntityManager()) { return em.find(Payment.class, 17L).amount; }
    }
    static void dirty(SessionFactory factory, SqlLog sql) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            Payment first = em.find(Payment.class, 17L);
            Payment second = em.find(Payment.class, 17L);
            System.out.println("same instance=" + (first == second));
            sql.clear();
            first.amount = 250; // Не вызываем ни save, ни merge: объект уже managed.
            System.out.println("before flush: database=" + databaseAmount(em) + ", updates=" + sql.updates());
            em.flush(); // UPDATE выполнен, но транзакция ещё не зафиксирована.
            System.out.println("after flush: database=" + databaseAmount(em) + ", updates=" + sql.updates());
            em.getTransaction().rollback();
        }
        System.out.println("after rollback: database=" + savedAmount(factory));
        tx(factory, em -> {
            Payment p = em.find(Payment.class, 17L);
            p.amount = 700;
            em.clear(); // Убираем объект вместе с несинхронизированными изменениями.
            System.out.println("after clear: managed=" + em.contains(p));
        });
        System.out.println("after clear+commit: database=" + savedAmount(factory));
    }
    static void merge(SessionFactory factory) {
        Payment detached;
        try (EntityManager em = factory.createEntityManager()) { detached = em.find(Payment.class, 17L); }
        detached.amount = 300;
        tx(factory, em -> {
            Payment managed = em.merge(detached); // Возвращённая копия принадлежит новому контексту.
            System.out.println("merge same instance=" + (managed == detached));
            System.out.println("contains original=" + em.contains(detached) + ", copy=" + em.contains(managed));
            detached.amount = 900; // Поздняя правка исходного объекта не переносится автоматически.
        });
        System.out.println("after merge: database=" + savedAmount(factory) + ", original=" + detached.amount);
    }
    static void query(SessionFactory factory, SqlLog sql) {
        tx(factory, em -> {
            Payment p = em.find(Payment.class, 17L); p.amount = 200;
            sql.clear();
            long result = em.createQuery("select p.amount from Payment p where p.id = 17", Long.class).getSingleResult();
            System.out.println("AUTO query=" + result + ", updates=" + sql.updates());
        });
        tx(factory, em -> {
            em.setFlushMode(FlushModeType.COMMIT);
            Payment p = em.find(Payment.class, 17L); p.amount = 300;
            sql.clear();
            long result = em.createQuery("select p.amount from Payment p where p.id = 17", Long.class).getSingleResult();
            System.out.println("COMMIT query=" + result + ", memory=" + p.amount + ", updates=" + sql.updates());
        });
        System.out.println("after commit: database=" + savedAmount(factory));
    }
    static void bulk(SessionFactory factory) {
        tx(factory, em -> {
            Payment p = em.find(Payment.class, 17L);
            int rows = em.createQuery("update Payment p set p.amount = 900 where p.id = 17").executeUpdate();
            System.out.println("bulk rows=" + rows + ", memory=" + p.amount + ", database=" + databaseAmount(em));
            System.out.println("find same instance=" + (p == em.find(Payment.class, 17L)));
            em.refresh(p); // Перечитать строку, а не получить прежний объект из контекста.
            System.out.println("after refresh: memory=" + p.amount);
        });
    }
}
