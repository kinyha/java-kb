package kb.shop.refunds;

import kb.shop.payments.persistence.PaymentRepository; // Внутренний пакет чужого модуля.

public class RefundService {
    private final PaymentRepository payments;

    RefundService(PaymentRepository payments) { this.payments = payments; }

    public String refund(String id) { return "REFUNDED " + payments.find(id).amount(); }
}
