package pay.proxy

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus

class Ledger { fun save(id: String) = "saved $id" }

@Component
class PaymentService(private val ledger: Ledger) {            // без плагина: final class
    @Transactional
    fun pay(id: String) = ledger.save(id)
}

@Component
open class RefundService(private val ledger: Ledger) {         // класс открыли руками...
    @Transactional
    fun refund(id: String) = ledger.save(id)                   // ...а метод остался final
}

@Configuration                                                  // full mode: тоже CGLIB-подкласс
class FeeConfig {
    @Bean fun feeRate() = 15
}

// Менеджер транзакций, который только печатает: виден факт BEGIN/COMMIT
class LoggingTxManager : AbstractPlatformTransactionManager() {
    override fun doGetTransaction(): Any = Any()
    override fun doBegin(tx: Any, def: TransactionDefinition) = println("    BEGIN")
    override fun doCommit(status: DefaultTransactionStatus) = println("    COMMIT")
    override fun doRollback(status: DefaultTransactionStatus) = println("    ROLLBACK")
}

@Configuration(proxyBeanMethods = false)                        // lite mode: прокси не нужен
@EnableTransactionManagement
class Infra {
    @Bean fun ledger() = Ledger()
    @Bean fun transactionManager(): PlatformTransactionManager = LoggingTxManager()
}

fun scenario(name: String, bean: Class<*>, call: (Any) -> String) {
    println("$name:")
    try {
        AnnotationConfigApplicationContext(Infra::class.java, bean).use { ctx ->
            val b = ctx.getBean(bean)
            println("    bean class: ${b.javaClass.simpleName}")
            println("    result: ${call(b)}")
        }
    } catch (e: Exception) {
        var c: Throwable = e
        while (c.cause != null && c.cause !== c) c = c.cause!!
        println("    ${c.javaClass.simpleName}: ${c.message?.lineSequence()?.first()}")
    }
}

fun main() {
    scenario("final class + @Transactional", PaymentService::class.java) { (it as PaymentService).pay("p-1") }
    scenario("open class, final method", RefundService::class.java) { (it as RefundService).refund("r-1") }
    scenario("final @Configuration", FeeConfig::class.java) { "feeRate=" + (it as FeeConfig).feeRate() }
}
