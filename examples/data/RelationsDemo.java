package kb.data;

import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RelationsDemo {
    @Entity(name = "Merchant") @Table(name = "merchants")
    public static class Merchant {
        @Id Long id;
        String name;
        @OneToMany(mappedBy = "merchant") List<Payment> payments = new ArrayList<>();
        protected Merchant() { }
        Merchant(long id) { this.id = id; this.name = "shop-" + id; }
        public String getName() { return name; }
        public List<Payment> getPayments() { return payments; }
    }
    @Entity(name = "Payment") @Table(name = "payments")
    public static class Payment {
        @Id Long id;
        long amount;
        @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "merchant_id") Merchant merchant;
        @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
        List<Refund> refunds = new ArrayList<>();
        protected Payment() { }
        Payment(long id, Merchant merchant) { this.id = id; this.amount = 100; moveTo(merchant); }
        void moveTo(Merchant next) {
            if (merchant != null) merchant.getPayments().remove(this);
            merchant = next; // Эта сторона задаёт значение внешнего ключа.
            if (next != null && !next.getPayments().contains(this)) next.getPayments().add(this);
        }
        void addRefund(Refund refund) { refunds.add(refund); refund.payment = this; }
        void removeRefund(Refund refund) { refunds.remove(refund); refund.payment = null; }
    }
    @Entity(name = "Refund") @Table(name = "refunds")
    public static class Refund {
        @Id Long id;
        @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id") Payment payment;
        protected Refund() { }
        Refund(long id) { this.id = id; }
    }
    static class SqlLog implements StatementInspector {
        final List<String> statements = new ArrayList<>();
        @Override public String inspect(String sql) { statements.add(sql); return sql; }
        long selects() { return statements.stream().filter(s -> s.toLowerCase().startsWith("select ")).count(); }
        void clear() { statements.clear(); }
    }
    public static void main(String[] args) {
        System.setProperty("org.jboss.logging.provider", "slf4j");
        SqlLog sql = new SqlLog();
        Configuration cfg = new Configuration().addAnnotatedClass(Merchant.class)
                .addAnnotatedClass(Payment.class).addAnnotatedClass(Refund.class);
        cfg.setProperty("hibernate.connection.url", "jdbc:h2:mem:relations;DB_CLOSE_DELAY=-1");
        cfg.setProperty("hibernate.connection.username", "sa");
        cfg.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        cfg.setProperty("hibernate.cache.use_second_level_cache", "false");
        cfg.setStatementInspector(sql);
        try (SessionFactory factory = cfg.buildSessionFactory()) {
            tx(factory, em -> {
                for (long i = 1; i <= 3; i++) {
                    Merchant m = new Merchant(i); em.persist(m);
                    Payment p = new Payment(16 + i, m); p.addRefund(new Refund(100 + i));
                    em.persist(p); // PERSIST идёт каскадом к Refund, Merchant сохраняется отдельно.
                }
            });
            String mode = args.length == 0 ? "fetch" : args[0];
            switch (mode) {
                case "fetch" -> fetch(factory, sql);
                case "owning" -> owning(factory);
                case "orphan" -> orphan(factory);
                case "closed" -> closed(factory);
                default -> throw new IllegalArgumentException("fetch | owning | orphan | closed");
            }
        }
    }
    static void tx(SessionFactory factory, Consumer<EntityManager> work) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            try { work.accept(em); em.getTransaction().commit(); }
            catch (RuntimeException e) { if (em.getTransaction().isActive()) em.getTransaction().rollback(); throw e; }
        }
    }
    static void fetch(SessionFactory factory, SqlLog sql) {
        for (String kind : List.of("lazy", "join", "graph")) {
            tx(factory, em -> {
                sql.clear();
                List<Payment> payments;
                if (kind.equals("join")) {
                    payments = em.createQuery("select p from Payment p join fetch p.merchant order by p.id", Payment.class)
                            .getResultList();
                } else {
                    var query = em.createQuery("select p from Payment p order by p.id", Payment.class);
                    if (kind.equals("graph")) {
                        EntityGraph<Payment> graph = em.createEntityGraph(Payment.class);
                        graph.addAttributeNodes("merchant");
                        query.setHint("jakarta.persistence.fetchgraph", graph);
                    }
                    payments = query.getResultList();
                }
                List<String> names = payments.stream().map(p -> merchantName(p.merchant)).toList();
                System.out.println(kind + ": names=" + names + ", selects=" + sql.selects());
            });
        }
    }
    static String merchantName(Merchant merchant) { return merchant.getName(); }
    static long merchantId(EntityManager em) {
        return em.unwrap(Session.class).doReturningWork(c -> {
            try (var s = c.prepareStatement("select merchant_id from payments where id = 17"); var row = s.executeQuery()) {
                row.next(); return row.getLong(1);
            }
        });
    }
    static void owning(SessionFactory factory) {
        tx(factory, em -> {
            Payment p = em.find(Payment.class, 17L); Merchant next = em.find(Merchant.class, 2L);
            next.getPayments().add(p); // Изменена только сторона mappedBy.
            em.flush();
            System.out.println("inverse-only: fk=" + merchantId(em));
        });
        tx(factory, em -> {
            Payment p = em.find(Payment.class, 17L);
            Merchant old = p.merchant; Merchant next = em.find(Merchant.class, 2L);
            old.getPayments().size(); next.getPayments().size(); // Загружаем обе коллекции до изменения.
            p.merchant = next; // SQL исправится, Java-коллекции сами не исправятся.
            em.flush();
            System.out.println("owning-only: fk=" + merchantId(em) + ", old.contains=" + old.getPayments().contains(p)
                    + ", new.contains=" + next.getPayments().contains(p));
        });
        tx(factory, em -> {
            Payment p = em.find(Payment.class, 17L);
            Merchant old = p.merchant;
            Merchant next = em.find(Merchant.class, 1L);
            p.moveTo(next); // Одновременно меняем внешний ключ и обе коллекции.
            em.flush();
            System.out.println("helper: fk=" + merchantId(em) + ", old.contains=" + old.getPayments().contains(p)
                    + ", new.contains=" + next.getPayments().contains(p));
        });
    }
    static void orphan(SessionFactory factory) {
        tx(factory, em -> {
            Payment p = em.find(Payment.class, 17L);
            System.out.println("before remove: refunds=" + p.refunds.size());
            p.removeRefund(p.refunds.getFirst());
            em.flush();
            long count = em.createQuery("select count(r) from Refund r where r.id = 101", Long.class).getSingleResult();
            System.out.println("after orphan flush: rows=" + count);
        });
    }
    static void closed(SessionFactory factory) {
        Payment p;
        try (EntityManager em = factory.createEntityManager()) { p = em.find(Payment.class, 17L); }
        try { System.out.println(merchantName(p.merchant)); }
        catch (org.hibernate.LazyInitializationException e) { System.out.println("after close: " + e.getClass().getSimpleName()); }
    }
}
