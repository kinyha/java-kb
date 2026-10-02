package kb.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kb.testing.Payments.Payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Срез: только MVC-часть, без базы, сервисов и банка.
@WebMvcTest(PaymentController.class)
class A_PaymentControllerTest {
    @Autowired MockMvcTester mvc;
    @Autowired ApplicationContext ctx;
    @MockitoBean PaymentService service; // Контроллеру нужен сервис: даём mock.

    @Test
    void validPaymentReturns201() {
        when(service.pay(any())).thenReturn(new Payment(1, "acc-1", 500, "APPROVED"));
        var result = mvc.post().uri("/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"acc-1\",\"cents\":500}");
        assertThat(result).hasStatus(201).bodyJson().extractingPath("$.status").isEqualTo("APPROVED");
        System.out.println("DEMO slice beans=" + ctx.getBeanDefinitionCount()
                + ", has PaymentRepository=" + (ctx.getBeanNamesForType(PaymentRepository.class).length > 0)
                + ", has DataSource=" + ctx.getBeanNamesForType(javax.sql.DataSource.class).length);
    }

    @Test
    void negativeAmountReturns400WithoutCallingService() {
        var result = mvc.post().uri("/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"acc-1\",\"cents\":-5}");
        assertThat(result).hasStatus(400);
        verify(service, never()).pay(any()); // Валидация остановила запрос раньше.
        System.out.println("DEMO invalid amount -> " + result.exchange().getResponse().getStatus());
    }
}
