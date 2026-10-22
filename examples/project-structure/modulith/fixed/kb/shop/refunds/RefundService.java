package kb.shop.refunds;

import kb.shop.payments.PaymentService;           // API модуля payments.
import org.springframework.stereotype.Service;

@Service
class RefundService {                             // package-private бин: Spring его найдёт и внедрит.
    private final PaymentService payments;

    RefundService(PaymentService payments) { this.payments = payments; }

    String refund(String id) { return "REFUNDED " + payments.find(id).amount(); }
}
