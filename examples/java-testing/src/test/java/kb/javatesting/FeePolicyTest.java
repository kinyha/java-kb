package kb.javatesting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class FeePolicyTest {
    final FeePolicy fees = new FeePolicy();

    @ParameterizedTest(name = "{0} cents -> fee {1}")
    @CsvSource({"1,1", "49,1", "50,1", "51,2", "100,2", "101,3",
                "9223372036854775807,184467440737095517"})
    void roundsUpWithoutOverflow(long amount, long expected) {
        // Oracle is a specified constant, not a copy of the production formula.
        assertThat(fees.cents(amount)).as("fee for %s", amount).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsInvalidAmount(long amount) {
        assertThatThrownBy(() -> fees.cents(amount))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("amount must be positive");
    }

    @Test void weakOracleAcceptsTheRoundingBug() {
        assertThat(fees.cents(101)).isPositive(); // even a buggy floor result 2 passes
    }
}
