package kb.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(Containers.class)
class D_PaymentFlowTest {
    @Autowired MockMvcTester mvc;
    @Autowired PaymentRepository repository;
    @MockitoBean BankClient bank; // Другая конфигурация — новый контекст в кеше.

    @Test
    void fullPathFromHttpToDatabase() {
        when(bank.charge(anyString(), anyLong())).thenReturn("APPROVED");
        long before = repository.count();
        var result = mvc.post().uri("/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"acc-2\",\"cents\":700}");
        assertThat(result).hasStatus(201);
        System.out.println("DEMO D: rows before=" + before + ", after=" + repository.count()
                + ", contexts=" + PaymentApp.CONTEXTS.get());
    }
}
