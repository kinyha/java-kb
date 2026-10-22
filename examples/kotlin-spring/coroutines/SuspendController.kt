package pay.coroutines

import kotlinx.coroutines.delay
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

fun thread(): String = Thread.currentThread().name

@RestController
class PaymentController {
    @GetMapping("/payments/{id}")
    suspend fun status(@PathVariable id: String): String {
        val before = thread()                 // поток запроса
        delay(50)                             // «ответ банка»: точка приостановки
        val after = thread()                  // кто нас возобновил
        return "$id PAID; before delay: $before; after delay: $after"
    }
}

fun main() {
    Thread.currentThread().name = "http-nio-1"                              // как поток Tomcat
    val mvc = MockMvcBuilders.standaloneSetup(PaymentController()).build()
    val started = mvc.perform(get("/payments/p-1")).andReturn()
    println("async started: ${started.request.isAsyncStarted}, response yet: '${started.response.contentAsString}'")
    started.getAsyncResult(2_000)                                            // дождаться завершения корутины
    val done = mvc.perform(asyncDispatch(started)).andReturn()
    println("status ${done.response.status}: ${done.response.contentAsString}")
}
