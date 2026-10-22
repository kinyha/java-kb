package pay.coroutines

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
import org.springframework.transaction.support.TransactionSynchronizationManager

class LoggingTxManager : AbstractPlatformTransactionManager() {       // блокирующий менеджер, как JpaTransactionManager
    override fun doGetTransaction(): Any = Any()
    override fun doBegin(tx: Any, def: TransactionDefinition) = println("    BEGIN on ${thread()}")
    override fun doCommit(status: DefaultTransactionStatus) = println("    COMMIT on ${thread()}")
    override fun doRollback(status: DefaultTransactionStatus) = println("    ROLLBACK on ${thread()}")
}

@Component
class RefundService {
    @Transactional
    suspend fun refund(id: String): String {
        println("    before delay: tx active=${TransactionSynchronizationManager.isActualTransactionActive()}")
        delay(50)
        return "refunded $id"
    }
}

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
class TxConfig {
    @Bean fun transactionManager(): PlatformTransactionManager = LoggingTxManager()
}

fun main() {
    AnnotationConfigApplicationContext(TxConfig::class.java, RefundService::class.java).use { ctx ->
        val service = ctx.getBean(RefundService::class.java)
        try { println("    result: " + runBlocking { service.refund("r-1") }) }
        catch (e: Exception) { println("    ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.first()}") }
    }
}
