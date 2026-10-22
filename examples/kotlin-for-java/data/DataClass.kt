package pay

data class Refund(val paymentId: String, var amount: Long) {  // var в data class разрешён
    var comment: String = ""                                     // тело класса: не входит в equals/hashCode/toString
}

@JvmRecord
data class Money(val amount: Long, val currency: String)          // компилируется в настоящий java.lang.Record

fun main() {
    val r = Refund("p-1", 500)
    val sent = hashSetOf(r)
    r.amount = 300                                // hashCode изменился, а объект лежит в старом бакете
    println("contains=${r in sent} size=${sent.size}")

    val a = Refund("p-2", 100).apply { comment = "клиент передумал" }
    val b = Refund("p-2", 100).apply { comment = "двойное списание" }
    println("equals=${a == b} $a")       // comment не участвует

    val half = a.copy(amount = 50)                // copy: новый объект, остальные поля скопированы
    println("$half comment='${half.comment}'")

    val (id, amount) = half                       // componentN(): деструктуризация по позиции
    println("$id $amount")

    println(Money(100, "RUB").javaClass.superclass.name)  // java.lang.Record
}
