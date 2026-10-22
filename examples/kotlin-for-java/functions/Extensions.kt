package pay

open class Payment(val amount: Long) {
    fun describe() = "member"                       // метод класса
}
class CardPayment(amount: Long) : Payment(amount)

fun Payment.kind() = "payment"                      // extension: static-метод ExtensionsKt.kind(Payment)
fun CardPayment.kind() = "card"                     // перегрузка, а не переопределение
fun Payment.describe() = "extension"                // член с той же сигнатурой всегда выигрывает

fun Long.withFee(percentX10: Int): Long = this + this * percentX10 / 1000

fun main() {
    val p: Payment = CardPayment(10_000)            // объявленный тип Payment, объект CardPayment
    println(p.kind())
    println((p as CardPayment).kind())
    println(p.describe())
    println(10_000L.withFee(15))
}
