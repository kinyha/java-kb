package kb.component.web;

import kb.component.payments.PaymentComponent;

public class PaymentController {
    private final PaymentComponent payments;

    public PaymentController(PaymentComponent payments) { this.payments = payments; }

    public String pay(String id, long amount) { return payments.pay(id, amount).status(); }
}
