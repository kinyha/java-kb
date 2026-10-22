package pay

class Payment(
    val id: String,                       // val: private final поле + getId()
    var status: String = "NEW",           // var: поле + getStatus() + setStatus(); значение по умолчанию
) {
    val isFinal: Boolean                  // свойство без поля: только геттер
        get() = status == "PAID" || status == "DECLINED"

    companion object {                    // вложенный класс Payment.Companion + static-поле Companion
        const val MAX_AMOUNT = 1_000_000L // const: настоящая static final константа
        fun of(id: String) = Payment(id)  // метод экземпляра Companion, не static
        @JvmStatic fun draft(id: String) = Payment(id, "DRAFT")  // + static-мост в Payment
    }
}

object FeePolicy {                        // синглтон: static INSTANCE + private конструктор
    var percent = 15                      // поле static, но геттер — метод экземпляра
    fun fee(amount: Long) = amount * percent / 1000
}

fun formatAmount(kopecks: Long) = "%d.%02d".format(kopecks / 100, kopecks % 100)  // top-level → PaymentKt
