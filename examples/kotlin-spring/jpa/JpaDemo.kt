package pay.jpa

import org.hibernate.Hibernate
import org.hibernate.SessionFactory
import org.hibernate.jpa.HibernatePersistenceConfiguration

fun main() {
    val emf = HibernatePersistenceConfiguration("pay")
        .managedClasses(Merchant::class.java, Payment::class.java, Refund::class.java)
        .jdbcUrl("jdbc:h2:mem:pay;DB_CLOSE_DELAY=-1")
        .property("jakarta.persistence.schema-generation.database.action", "create")
        .property("hibernate.generate_statistics", "true")
        .property("hibernate.logging.level", "WARN")
        .createEntityManagerFactory()
    val stats = emf.unwrap(SessionFactory::class.java).statistics

    emf.createEntityManager().apply {
        transaction.begin()
        persist(Merchant(1, "Coffee Shop"))
        persist(Payment(10, 350, find(Merchant::class.java, 1L)))
        transaction.commit(); close()
    }

    // 1. Загрузка платежа: сколько запросов и загружен ли продавец
    stats.clear()
    val em = emf.createEntityManager()
    try {
        val p = em.find(Payment::class.java, 10L)
        println("1. payment loaded: merchant class=${p.merchant.javaClass.simpleName}, " +
            "initialized=${Hibernate.isInitialized(p.merchant)}, statements=${stats.prepareStatementCount}")
    } catch (e: Exception) {
        println("1. ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.first()}")
    }
    em.close()

    // 2. data class как сущность: hashCode меняется после persist
    emf.createEntityManager().apply {
        val refund = Refund(amount = 100, merchant = getReference(Merchant::class.java, 1L))
        val pending = hashSetOf(refund)
        transaction.begin(); persist(refund); transaction.commit()   // id присвоен
        println("2. refund id=${refund.id}, still in set: ${refund in pending}")
        close()
    }

    // 3. toString data class тянет lazy-связь после закрытия сессии
    val detached = emf.createEntityManager().run {
        val r = createQuery("select r from Refund r", Refund::class.java).singleResult
        close(); r
    }
    try { println("3. $detached") }
    catch (e: Exception) { println("3. ${e.javaClass.simpleName}: ${e.message}") }
    emf.close()
}
