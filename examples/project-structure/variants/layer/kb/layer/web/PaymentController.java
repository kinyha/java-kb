package kb.layer.web;

import kb.layer.service.PaymentService;

public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) { this.service = service; }

    public String pay(String id, long amount) { return service.pay(id, amount).status(); }
}
