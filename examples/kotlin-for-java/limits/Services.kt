package pay

class PaymentService {                      // final class, final методы
    fun charge(amount: Long) = "charged $amount"
}

open class RefundService {                  // open: класс можно наследовать
    open fun refund(amount: Long) = "refunded $amount"
    fun audit() = "audit"                   // метод всё равно final, пока не open
}
