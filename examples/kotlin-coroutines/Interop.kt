package pay.interop

import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

// Java-клиент банка: асинхронный API на CompletableFuture
fun bankApi(id: String, latencyMs: Long): CompletableFuture<String> =
    CompletableFuture.supplyAsync { Thread.sleep(latencyMs); "bank-ok:$id" }

suspend fun charge(id: String, latencyMs: Long = 50): String =
    bankApi(id, latencyMs).await()                 // приостановка без блокировки потока

val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// Для Java-кода: корутина снаружи выглядит как CompletableFuture
fun chargeForJava(id: String): CompletableFuture<String> = appScope.future { charge(id) }

fun main() = runBlocking {
    println(charge("p-1"))
    println(chargeForJava("p-2").get())

    val slow = bankApi("p-3", 500)
    val r = withTimeoutOrNull(50) { slow.await() } // отмена корутины отменяет и future
    println("timeout result=$r future cancelled=${slow.isCancelled}")
}
