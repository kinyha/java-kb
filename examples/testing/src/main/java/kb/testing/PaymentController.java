package kb.testing;

import jakarta.validation.Valid;
import kb.testing.Payments.Payment;
import kb.testing.Payments.PaymentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentController {
    private final PaymentService service;
    PaymentController(PaymentService service) { this.service = service; }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    Payment pay(@Valid @RequestBody PaymentRequest request) { return service.pay(request); }
}
