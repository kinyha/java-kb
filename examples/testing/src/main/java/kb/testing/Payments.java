package kb.testing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

final class Payments {
    private Payments() {}

    record PaymentRequest(@NotBlank String account, @Positive long cents) {}

    record Payment(long id, String account, long cents, String status) {}
}
