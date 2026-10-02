package kb.modules.notifications;

import kb.modules.payments.internal.PaymentLedger;

public final class NotificationSender {
    private final PaymentLedger ledger = new PaymentLedger();
    public String message(String id) { return ledger.caption(id); }
}
