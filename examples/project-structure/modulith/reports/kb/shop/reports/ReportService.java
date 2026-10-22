package kb.shop.reports;

import kb.shop.payments.persistence.PaymentRepository; // Новое нарушение, появилось после заморозки.

public class ReportService {
    private final PaymentRepository payments;

    ReportService(PaymentRepository payments) { this.payments = payments; }

    public long total(String id) { return payments.find(id).amount(); }
}
