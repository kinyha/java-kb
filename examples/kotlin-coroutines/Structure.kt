package pay.structure

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

var t0 = System.nanoTime()                       // точка отсчёта для log; сбрасывается перед каждым опытом
fun log(msg: String) = println("%4d ms  %s".format((System.nanoTime() - t0) / 1_000_000 / 10 * 10, msg))

// Три проверки платежа в одном scope: падение одной отменяет остальные.
suspend fun authorize(id: String): Long = coroutineScope {
    val fraud = async {
        try { delay(200); log("fraud done"); 3L }
        catch (e: CancellationException) { log("fraud cancelled"); throw e }   // отмену пробрасываем дальше
    }
    val limits = async<Long> {
        delay(50)
        throw IllegalStateException("limits service 503")
    }
    launch {
        try { delay(300); log("fx rate cached") }
        finally { log("fx finally, active=$isActive") }                         // finally выполняется и при отмене
    }
    fraud.await() + limits.await()
}

// Блокирующая сверка: 10 пачек по 40 мс. Отмена кооперативная.
fun processBatch() = Thread.sleep(40)

suspend fun reconcile(cooperative: Boolean, done: AtomicInteger) {
    repeat(10) {
        if (cooperative) currentCoroutineContext().ensureActive()   // точка проверки отмены
        processBatch()
        done.incrementAndGet()
    }
}

// Типичная ошибка: catch (e: Exception) глотает CancellationException.
suspend fun pollStatuses() {
    repeat(5) { i ->
        try { delay(50); log("poll $i") }
        catch (e: Exception) { log("poll $i: swallowed ${e::class.simpleName}") }
    }
}

fun main() = runBlocking {
    coroutineScope { async { delay(1) }.await(); launch { delay(1) } }   // прогрев: загрузка классов не попадёт в замер
    t0 = System.nanoTime()
    try { authorize("p-1") }
    catch (e: IllegalStateException) { log("caller got: ${e.message}") }

    println()
    for (cooperative in listOf(false, true)) {
        val done = AtomicInteger()
        val result = withContext(Dispatchers.Default) {
            withTimeoutOrNull(100) { reconcile(cooperative, done); "finished" }
        }
        println("cooperative=$cooperative result=$result batches=${done.get()}")
    }

    println()
    t0 = System.nanoTime()
    val job = launch { pollStatuses(); log("pollStatuses returned normally") }
    delay(120)
    log("cancel"); job.cancelAndJoin()
}
