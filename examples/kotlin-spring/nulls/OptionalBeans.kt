package pay.nulls

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Component

class SmsGateway

@Component
class Notifier(private val sms: SmsGateway?) {       // nullable: зависимость необязательна
    fun channels() = if (sms == null) "email only" else "email + sms"
}

fun main() {
    try {
        AnnotationConfigApplicationContext(Notifier::class.java).use { ctx ->   // SmsGateway не зарегистрирован
            println("notifier: " + ctx.getBean(Notifier::class.java).channels())
        }
    } catch (e: Exception) {
        var c: Throwable = e
        while (c.cause != null) c = c.cause!!
        println(c.javaClass.simpleName + ": " + c.message?.substringBefore(": expected"))
    }
}
