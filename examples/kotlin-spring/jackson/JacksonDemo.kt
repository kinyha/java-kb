package pay.jackson

import tools.jackson.databind.json.JsonMapper

data class PaymentRequest(
    val id: String,
    val amount: Long,
    val currency: String = "RUB",        // значение по умолчанию: клиент может не присылать
    val comment: String? = null,
)

fun main(args: Array<String>) {
    val builder = JsonMapper.builder()
    if ("--kotlin-module" in args) {     // так модуль регистрирует Spring Boot, если он есть в classpath
        builder.addModule(Class.forName("tools.jackson.module.kotlin.KotlinModule\$Builder")
            .getConstructor().newInstance().let { it.javaClass.getMethod("build").invoke(it) as tools.jackson.databind.JacksonModule })
    }
    val mapper = builder.build()
    for (json in listOf(
        """{"id":"p-1","amount":350,"currency":"EUR"}""",
        """{"id":"p-2","amount":350}""",
        """{"amount":350}""",
    )) {
        val r = runCatching { mapper.readValue(json, PaymentRequest::class.java) }
        println("$json -> " + r.fold({ it.toString() }, { "${it.javaClass.simpleName}: ${it.message?.lineSequence()?.first()}" }))
    }
}
