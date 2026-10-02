package kb.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

// Точные ожидания и границы правил.
class StrongFeeTest {
    final FeeCalculator calc = new FeeCalculator();

    @ParameterizedTest(name = "{0} kop, partner={1} -> {2}")
    @CsvSource({
            "5000,   false, 100",   // 2 %
            "99999,  false, 1999",  // последняя сумма по 2 %
            "100000, false, 1500",  // граница: уже 1,5 %
            "200000, true,  1500",  // партнёру половина
            "1000,   false, 30",    // 20 коп меньше минимума
            "1,      false, 30",
    })
    void fee(long amount, boolean partner, long expected) {
        assertThat(calc.fee(amount, partner)).isEqualTo(expected);
    }

    @Test void rejectsZeroAndNegative() {
        assertThatThrownBy(() -> calc.fee(0, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc.fee(-1, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
