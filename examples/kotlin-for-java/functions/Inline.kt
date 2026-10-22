package pay

sealed interface Event
data class PaymentEvent(val id: String) : Event
data class RefundEvent(val id: String) : Event

// reified: тип T доступен в теле, потому что тело копируется в место вызова с конкретным типом.
inline fun <reified T : Event> List<Event>.only(): List<T> = filter { it is T }.map { it as T }

// Java-способ: тип приходится передавать явно, Class<T> — объект во время выполнения.
fun <T : Event> List<Event>.onlyOf(type: Class<T>): List<T> = filter(type::isInstance).map(type::cast)

inline fun <T> audited(action: String, block: () -> T): T {
    println("start $action")
    return block().also { println("done $action") }
}

fun firstRefund(events: List<Event>): String {
    events.forEach {                                // forEach — inline: return выходит из firstRefund
        if (it is RefundEvent) return it.id
    }
    return "none"
}

fun main() {
    val events = listOf(PaymentEvent("p-1"), RefundEvent("r-1"), PaymentEvent("p-2"))
    println(events.only<PaymentEvent>())
    println(events.onlyOf(RefundEvent::class.java))
    println(audited("refund") { firstRefund(events) })
}
