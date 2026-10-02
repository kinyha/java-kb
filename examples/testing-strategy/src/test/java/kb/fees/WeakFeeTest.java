package kb.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// Покрывает каждую строку и ветку, но почти ничего не проверяет.
class WeakFeeTest {
    final FeeCalculator calc = new FeeCalculator();

    @Test void smallPayment() { assertThat(calc.fee(5_000, false)).isPositive(); }
    @Test void largePartnerPayment() { assertThat(calc.fee(200_000, true)).isPositive(); }
    @Test void rejectsZero() { assertThatThrownBy(() -> calc.fee(0, false)).isInstanceOf(IllegalArgumentException.class); }
}
