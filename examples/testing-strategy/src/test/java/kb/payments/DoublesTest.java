package kb.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import kb.payments.Payments.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DoublesTest {
    interface Factory { PaymentServiceV1 create(PaymentRepository r, FraudCheck f, Notifier n); }

    static Stream<Arguments> versions() {
        return Stream.of(Arguments.of("V1", (Factory) PaymentServiceV1::new), Arguments.of("V2", (Factory) PaymentServiceV2::new));
    }

    // Стаб отвечает заранее заданным значением; проверяем результат и состояние.
    @ParameterizedTest(name = "{0}") @MethodSource("versions")
    void stubAndFake_checkState(String v, Factory factory) {
        var repo = new InMemoryPaymentRepository();                  // Fake.
        FraudCheck highRisk = amount -> 95;                          // Stub: лямбда, без Mockito.
        var service = factory.create(repo, highRisk, id -> {});      // Dummy-уведомитель.

        assertThat(service.accept("p-1", 500)).isEqualTo("DECLINED");
        assertThat(repo.rows.get("p-1").status()).isEqualTo("DECLINED");
        assertThat(service.accept("p-1", 500)).isEqualTo("DUPLICATE");
    }

    // Мок проверяет взаимодействие, которое и есть результат: уведомление ушло один раз.
    @ParameterizedTest(name = "{0}") @MethodSource("versions")
    void mock_verifiesOutgoingCommand(String v, Factory factory) {
        Notifier notifier = mock(Notifier.class);
        var service = factory.create(new InMemoryPaymentRepository(), amount -> 10, notifier);

        service.accept("p-2", 500);
        service.accept("p-2", 500);                                   // Дубль — без второго уведомления.

        verify(notifier, times(1)).paymentAccepted("p-2");
    }

    // Переопределённый тест: фиксирует, КАК сервис ходит в репозиторий. Падает на V2 намеренно.
    @Tag("fragile")
    @ParameterizedTest(name = "{0}") @MethodSource("versions")
    void overMocked_verifiesEveryCall(String v, Factory factory) {
        PaymentRepository repo = mock(PaymentRepository.class);
        when(repo.findById("p-3")).thenReturn(Optional.empty());
        FraudCheck fraud = mock(FraudCheck.class);
        when(fraud.score(anyLong())).thenReturn(10);
        var service = factory.create(repo, fraud, mock(Notifier.class));

        service.accept("p-3", 500);

        verify(repo).findById("p-3");
        verify(repo).save(any());
        verifyNoMoreInteractions(repo);
    }

    static class RecordingFraudCheck implements FraudCheck {
        final List<String> calls = new ArrayList<>();
        public int score(long amount) { calls.add("real score(" + amount + ")"); return 10; }
    }

    // spy вызывает настоящий метод даже внутри when(...).
    @Test
    void spy_callsRealMethodInsideWhen() {
        var real = new RecordingFraudCheck();
        var calls = real.calls;
        RecordingFraudCheck spy = spy(real);

        when(spy.score(500)).thenReturn(99);                          // Здесь real уже вызван.
        doReturn(99).when(spy).score(700);                            // А здесь — нет.

        System.out.println("spy real calls: " + calls);
        assertThat(calls).containsExactly("real score(500)");
    }
}
