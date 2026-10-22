package pay.mechanism

import kotlin.coroutines.*

// Голый механизм без kotlinx.coroutines: только компилятор и kotlin.coroutines из stdlib.
var pending: Continuation<Int>? = null           // «сетевой клиент» держит continuation до ответа

suspend fun fraudScore(id: String): Int = suspendCoroutine { cont ->
    pending = cont                               // запомнили, куда вернуть результат
    println("  fraud request sent for $id")
}                                                // лямбда вернулась — функция приостановлена

suspend fun authorize(id: String, amount: Long): String {
    println("  authorize: start, label 0")
    val score = fraudScore(id)                   // точка приостановки
    println("  authorize: resumed with score=$score on ${Thread.currentThread().name}")
    return if (score < 10 && amount <= 50_000) "APPROVED" else "DECLINED"
}

fun main() {
    val done = Continuation<String>(EmptyCoroutineContext) { println("  completion: $it") }

    // suspend-функция для JVM — обычный метод с лишним параметром Continuation
    @Suppress("UNCHECKED_CAST")
    val raw = ::authorize as (String, Long, Continuation<String>) -> Any?
    val result = raw("p-1", 1_000, done)
    println("1. authorize returned: $result")    // не String, а маркер приостановки

    println("2. thread is free, doing other work")

    val responder = Thread({ pending!!.resume(3) }, "fraud-response")   // ответ пришёл в другом потоке
    responder.start(); responder.join()
    println("3. main: end")
}
