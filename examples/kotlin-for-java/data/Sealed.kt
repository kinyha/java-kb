package pay

sealed interface PayResult                                   // все наследники — в этом модуле и пакете
data class Paid(val txId: String) : PayResult
data class Declined(val reason: String) : PayResult
data object Timeout : PayResult                              // синглтон с читаемым toString

data class Charge(val amount: Long) {
    init { require(amount > 0) { "amount must be positive: $amount" } }   // проверка в первичном конструкторе
}

fun httpStatus(r: PayResult): Int = when (r) {               // выражение: без else, компилятор проверит полноту
    is Paid -> 200                                           // smart cast: r здесь уже Paid
    is Declined -> if (r.reason == "fraud") 403 else 402
    Timeout -> 504
}

fun main() {
    listOf(Paid("tx-1"), Declined("fraud"), Declined("no money"), Timeout)
        .forEach { println("$it -> ${httpStatus(it)}") }
    val c = Charge(100)
    println(runCatching { c.copy(amount = -5) })             // copy вызывает тот же конструктор
}
