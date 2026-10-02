package kb.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kb.payments.Payments.Payment;
import kb.payments.Payments.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Один набор правил для всех реализаций репозитория: фейк обязан вести себя как база. */
abstract class PaymentRepositoryContract {
    PaymentRepository repo;

    abstract PaymentRepository create() throws Exception;

    @BeforeEach void setUp() throws Exception { repo = create(); }

    @Test void savedPaymentIsFound() {
        repo.save(new Payment("p-1", 500, "ACCEPTED"));
        assertThat(repo.findById("p-1")).contains(new Payment("p-1", 500, "ACCEPTED"));
        assertThat(repo.existsById("p-1")).isTrue();
    }

    @Test void unknownPaymentIsAbsent() {
        assertThat(repo.findById("p-404")).isEmpty();
        assertThat(repo.existsById("p-404")).isFalse();
    }

    @Test void secondSaveWithSameIdIsRejectedAndKeepsFirst() {
        repo.save(new Payment("p-1", 500, "ACCEPTED"));
        assertThatThrownBy(() -> repo.save(new Payment("p-1", 900, "DECLINED"))).isInstanceOf(IllegalStateException.class);
        assertThat(repo.findById("p-1")).contains(new Payment("p-1", 500, "ACCEPTED"));
    }
}
