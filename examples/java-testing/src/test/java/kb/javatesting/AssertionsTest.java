package kb.javatesting;

import java.math.BigDecimal;
import java.util.List;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class AssertionsTest {
    @Test void preemptiveTimeoutChangesTheThreadLocalScope() {
        var context = new ThreadLocal<String>();
        context.set("TX");
        try {
            String same = assertTimeout(Duration.ofSeconds(1), context::get);
            String other = assertTimeoutPreemptively(Duration.ofSeconds(1), context::get);
            assertThat(same).isEqualTo("TX");
            assertThat(other).isNull();
            System.out.println("timeout same-thread=TX; preemptive other-thread=null");
        } finally { context.remove(); }
    }
    @Test void chosenEqualityChangesTheContract() {
        var amount = new BigDecimal("2.0");
        assertThat(amount).isEqualByComparingTo("2.00");
        var scaleFailure = assertThrows(AssertionError.class,
                () -> assertThat(amount).isEqualTo(new BigDecimal("2.00")));
        System.out.println("BigDecimal numeric comparison=pass, equals(scale)=AssertionError");
        assertThat(scaleFailure).hasMessageContaining("2.00");
        var rows = List.of("P2", "P1");
        assertThat(rows).containsExactlyInAnyOrder("P1", "P2");
        assertThrows(AssertionError.class, () -> assertThat(rows).containsExactly("P1", "P2"));
        System.out.println("rows any-order=pass, ordered=AssertionError");
    }
    @Test void softAssertionsNeedATerminalCheck() {
        var soft = new SoftAssertions();
        soft.assertThat(new FeePolicy().cents(101)).as("fee").isEqualTo(2);
        soft.assertThat("APPROVED").as("status").isEqualTo("DECLINED");
        System.out.println("soft collected=2; before assertAll no exception");
        var failure = assertThrows(AssertionError.class, soft::assertAll);
        assertThat(failure).hasMessageContaining("fee").hasMessageContaining("status");
        System.out.println("soft assertAll=AssertionError");
    }
}
