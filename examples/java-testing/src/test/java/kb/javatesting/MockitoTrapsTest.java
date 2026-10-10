package kb.javatesting;

import java.util.ArrayList;
import java.util.List;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.exceptions.misusing.*;
import org.mockito.quality.Strictness;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import kb.javatesting.PaymentService.*;

class MockitoTrapsTest {
    @Test void unstubbedDefaultCanSelectABusinessBranch() {
        Bank bank = mock(Bank.class);
        var saved = new ArrayList<Receipt>();
        var service = new PaymentService(new FeePolicy(), bank, saved::add, Clock.systemUTC());
        assertThatThrownBy(() -> service.pay("P7", 101)).isInstanceOf(Declined.class);
        assertThat(saved).isEmpty();
        System.out.println("unstubbed boolean=false -> Declined");
    }
    @Test void unusedStubIsDetectedAtStrictSessionClose() {
        var session = mockitoSession().strictness(Strictness.STRICT_STUBS).startMocking();
        Bank bank = mock(Bank.class); // created while the strict session is active
        when(bank.charge("P7", 101)).thenReturn(true);
        assertThrows(UnnecessaryStubbingException.class, session::finishMocking);
        System.out.println("unused stub -> UnnecessaryStubbingException");
    }
    @Test void aSpyRunsTheMethodWhileWhenIsEvaluated() {
        List<String> spy = spy(new ArrayList<String>());
        assertThrows(IndexOutOfBoundsException.class, () -> when(spy.get(0)).thenReturn("P7"));
        doReturn("P7").when(spy).get(0); // no get(0) during setup
        assertThat(spy.get(0)).isEqualTo("P7");
        assertThat(spy).isEmpty(); // stub did not add an element
        System.out.println("spy when(get(0))=IndexOutOfBoundsException, doReturn=P7, size=0");
    }
    @Test void captorObservesArgumentsAndMatchersMustBeConsistent() {
        Bank bank = mock(Bank.class);
        assertThrows(InvalidUseOfMatchersException.class,
                () -> when(bank.charge(eq("P7"), 101)).thenReturn(true));
        when(bank.charge(eq("P7"), eq(101L))).thenReturn(true);
        assertThat(bank.charge("P7", 101)).isTrue();
        var id = ArgumentCaptor.forClass(String.class);
        verify(bank).charge(id.capture(), eq(101L));
        assertThat(id.getValue()).isEqualTo("P7");
        System.out.println("mixed matchers=InvalidUseOfMatchersException; captured id=P7");
    }
    @Test void inlineMakerCanStubFinalMethodsWithExplicitAgent() {
        var fees = mock(FeePolicy.class);
        when(fees.cents(101)).thenReturn(99L); // FeePolicy is final
        assertThat(fees.cents(101)).isEqualTo(99);
        System.out.println("final FeePolicy mock=99; real FeePolicy=3");
        assertThat(new FeePolicy().cents(101)).isEqualTo(3);
    }
}
