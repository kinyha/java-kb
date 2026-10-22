package kb.feature.payments;

public class PaymentController {                  // Единственный public-тип пакета.
    private final PaymentService service;

    PaymentController(PaymentService service) { this.service = service; }

    public String pay(String id, long amount) { return service.pay(id, amount).status(); }
}
