package pay.flows

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Cold: код внутри flow { } выполняется заново для каждого collect.
fun paymentsFromDb(): Flow<String> = flow {
    println("   SELECT * FROM payments")
    emit("p-1"); emit("p-2")
}

fun main() = runBlocking {
    val payments = paymentsFromDb()
    println("1. flow created, nothing executed")
    payments.collect { println("   a: $it") }
    payments.collect { println("   b: $it") }

    // Backpressure без буфера: emit приостанавливается, пока collector не закончит
    flow { for (i in 1..3) { println("   emit $i"); emit(i) } }
        .collect { println("   process $it"); delay(10) }

    // Hot: StateFlow хранит последнее значение и сливает быстрые обновления
    val status = MutableStateFlow("NEW")
    val ui = launch { status.collect { println("2. ui sees $it") } }
    yield()                                       // дать подписчику стартовать
    status.value = "AUTHORIZED"                   // без приостановки между присваиваниями
    status.value = "CAPTURED"
    status.value = "CAPTURED"                     // то же значение — не событие
    yield()
    ui.cancel()

    // Hot: SharedFlow без replay — событие без подписчиков теряется
    val events = MutableSharedFlow<String>()
    events.emit("refund r-1")                     // подписчиков нет: emit вернулся сразу
    val audit = launch { events.collect { println("3. audit got $it") } }
    yield()
    events.emit("refund r-2")
    yield()
    audit.cancel()
}
