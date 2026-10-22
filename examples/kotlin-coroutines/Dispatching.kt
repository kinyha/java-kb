package pay.dispatching

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

fun jdbcCall() = Thread.sleep(100)                 // блокирующий вызов: JDBC, старый HTTP-клиент
suspend fun httpCall() = delay(100)                // неблокирующий: поток отпускается

// 100 платежей в ночной сверке, каждый — один вызов на 100 мс.
suspend fun batch(name: String, ctx: CoroutineContext, call: suspend () -> Unit) = coroutineScope {
    val threads = ConcurrentHashMap.newKeySet<Long>()
    val start = System.nanoTime()
    var healthLatency = 0L
    repeat(100) {
        launch(ctx) { threads += Thread.currentThread().threadId(); call() }
    }
    val h = System.nanoTime()
    launch(ctx) { healthLatency = (System.nanoTime() - h) / 1_000_000 }   // быстрая задача после сотни медленных
        .join()
    coroutineContext.job.children.forEach { it.join() }
    val ms = (System.nanoTime() - start) / 1_000_000
    if (name != "warm-up") println("%-24s elapsed=%4d ms  threads=%3d  health-check waited %3d ms".format(name, ms, threads.size, healthLatency))
}

fun main() = runBlocking {
    println("cpus=${Runtime.getRuntime().availableProcessors()}")
    batch("warm-up", Dispatchers.Default) { httpCall() }
    batch("Default + Thread.sleep", Dispatchers.Default) { jdbcCall() }
    batch("IO + Thread.sleep", Dispatchers.IO) { jdbcCall() }
    batch("Default + delay", Dispatchers.Default) { httpCall() }
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher().use { vt ->
        batch("virtual threads + sleep", vt) { jdbcCall() }
    }
}
