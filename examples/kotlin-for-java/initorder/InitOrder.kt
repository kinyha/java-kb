package pay

// NPE без единой строчки Java: open-метод вызывается из конструктора базового класса.
open class Gateway {
    init { println("registered: " + describe()) }        // вызывается до инициализации полей наследника
    open fun describe() = "gateway"
}

class BankGateway(val bankName: String) : Gateway() {
    override fun describe() = "bank " + bankName.uppercase()   // bankName объявлен не-null, но ещё не присвоен
}

fun main() {
    try { BankGateway("sber") }
    catch (e: NullPointerException) { println(e.message) }
}
