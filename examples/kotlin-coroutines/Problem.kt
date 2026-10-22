package pay.problem

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

suspend fun fraudScore(id: String): Int { delay(100); return id.length % 10 }   // сетевой вызов, 100 мс
suspend fun limitLeft(id: String): Long { delay(80); return 50_000 }           // ещё один, 80 мс

// Последовательный по виду код; два вызова идут параллельно, поток не блокируется.
suspend fun authorize(id: String, amount: Long): Boolean = coroutineScope {
    val score = async { fraudScore(id) }
    val limit = async { limitLeft(id) }
    score.await() < 9 && amount <= limit.await()
}

fun main() = runBlocking {                                   // один поток: main
    val threads = ConcurrentHashMap.newKeySet<String>()
    val start = System.nanoTime()
    val results = (1..10_000).map { i ->
        async {
            threads += Thread.currentThread().name
            authorize("p-$i", 1_000)
        }
    }.awaitAll()
    val ms = (System.nanoTime() - start) / 1_000_000
    println("authorizations=${results.size} threads=$threads elapsed=${ms} ms")
}
