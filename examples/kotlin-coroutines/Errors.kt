package pay.errors

import kotlinx.coroutines.*

suspend fun send(channel: String): String {
    delay(if (channel == "sms") 20L else 50L)
    if (channel == "sms") throw IllegalStateException("sms gateway down")
    return "$channel sent"
}

fun main() = runBlocking {
    // 1. async без await: исключение всё равно уходит родителю и отменяет соседей
    try {
        coroutineScope {
            val email = async { send("email") }
            val sms = async { send("sms") }        // await для sms не вызываем
            println("1. email: ${email.await()}")
        }
    } catch (e: IllegalStateException) { println("1. scope failed: ${e.message}") }

    // 2. supervisorScope: падение одного ребёнка не трогает остальных
    supervisorScope {
        val jobs = listOf("email", "sms", "push").associateWith { async { send(it) } }
        for ((channel, d) in jobs) {
            try { println("2. ${d.await()}") }
            catch (e: IllegalStateException) { println("2. $channel failed: ${e.message}") }
        }
    }

    // 3. Долгоживущий scope сервиса: Job против SupervisorJob
    val handler = CoroutineExceptionHandler { _, e -> println("   handler: ${e.message}") }
    for ((name, job) in listOf("Job()" to Job(), "SupervisorJob()" to SupervisorJob())) {
        val scope = CoroutineScope(job + Dispatchers.Default + handler)
        scope.launch { send("sms") }.join()                      // первая задача падает
        val second = scope.launch { println("   second receipt: ${send("email")}") }
        second.join()
        println("3. $name: scope active=${scope.isActive}, second cancelled=${second.isCancelled}")
    }
}
