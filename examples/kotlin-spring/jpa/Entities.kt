package pay.jpa

import jakarta.persistence.*

@Entity
class Merchant(
    @Id val id: Long,
    var name: String,
)

@Entity
class Payment(
    @Id val id: Long,
    var amount: Long,
    @ManyToOne(fetch = FetchType.LAZY) val merchant: Merchant,   // хотим загрузку по требованию
)

@Entity
data class Refund(                                                // так делать не надо: пример ошибки
    @Id @GeneratedValue var id: Long? = null,
    var amount: Long,
    @ManyToOne(fetch = FetchType.LAZY) var merchant: Merchant? = null,
)
