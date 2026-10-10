package kb.javatesting;

import java.time.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import kb.javatesting.PaymentService.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock Bank bank;
    final ArrayList<Receipt> saved = new ArrayList<>(); // fake state, not PostgreSQL
    final Clock clock = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);
    PaymentService service() { return new PaymentService(new FeePolicy(), bank, saved::add, clock); }

    @Test void approvedReceiptHasTheRealFeeAndTime() {
        when(bank.charge("P7", 101)).thenReturn(true);
        var receipt = service().pay("P7", 101);
        assertThat(receipt).isEqualTo(new Receipt("P7", 101, 3, clock.instant()));
        assertThat(saved).containsExactly(receipt);
        verify(bank).charge("P7", 101); // effect at the outgoing bank boundary
        System.out.println("approved: fee=3 saved=1 bank charge verified");
    }
    @Test void invalidAmountNeverChargesBank() {
        assertThatThrownBy(() -> service().pay("P7", 0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(bank);
        assertThat(saved).isEmpty();
        System.out.println("invalid: bank calls=0 saved=0");
    }
    @Test void declinedBankMustNotPersist() {
        when(bank.charge("P7", 101)).thenReturn(false);
        assertThatThrownBy(() -> service().pay("P7", 101)).isInstanceOf(Declined.class);
        assertThat(saved).isEmpty();
        System.out.println("declined: saved=0");
    }
    @Test void timeoutMustNotPersist() {
        when(bank.charge("P7", 101)).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> service().pay("P7", 101)).hasMessage("timeout");
        assertThat(saved).isEmpty();
        System.out.println("timeout: saved=0");
    }
}
