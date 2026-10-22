package pay

class Receipts {
    // owner: String — не-null тип. Компилятор вставит проверку параметра в начало метода.
    fun line(owner: String, amount: Long): String = "$owner paid $amount"
}

// Тип объявлен явно: проверка встаёт прямо в месте присваивания.
fun ownerDeclared(repo: LegacyAccounts, id: String): Int {
    val owner: String = repo.findOwner(id)
    return owner.length
}

// Тип выведен: owner имеет platform type String!, проверки нет.
fun ownerInferred(repo: LegacyAccounts, id: String): Int {
    val owner = repo.findOwner(id)
    println("  owner loaded")                 // код идёт дальше с null внутри
    return owner.length
}

class Checkout {
    lateinit var gateway: String              // заполнит DI-контейнер... если заполнит
    fun pay() = "via $gateway"
}

fun main() {
    val repo = LegacyAccounts()
    run("declared") { ownerDeclared(repo, "closed-42") }
    run("inferred") { ownerInferred(repo, "closed-42") }
    run("!!") { repo.findOwner("closed-42")!!.length }
    run("lateinit") { Checkout().pay() }
    run("java -> kotlin") { JavaCheckout.printReceipt(Receipts()) }
}

fun run(name: String, block: () -> Any) {
    println("$name:")
    try { println("  ok " + block()) }
    catch (e: Exception) { println("  ${e.javaClass.name}: ${e.message}") }
}
