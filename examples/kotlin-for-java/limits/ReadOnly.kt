package pay

class FeeTable {
    private val fees = mutableListOf(150L, 200L)
    val view: List<Long> get() = fees              // наружу — List, но это тот же ArrayList
}

fun main() {
    val table = FeeTable()
    println(JavaLedger.tryAdd(table.view))          // Java видит java.util.List и вызывает add
    println(table.view)                             // состояние FeeTable изменилось

    val many: List<Long> = listOf(150L, 200L)       // vararg → Arrays.asList
    println(many.javaClass.name + ": " + JavaLedger.trySet(many) + ", " + JavaLedger.tryAdd(many))

    val one: List<Long> = listOf(150L)              // один элемент → Collections.singletonList
    println(one.javaClass.name + ": " + JavaLedger.trySet(one))

    println((table.view as MutableList<Long>).removeAll { it > 100 })  // каст из Kotlin тоже проходит
    println(table.view)
}
