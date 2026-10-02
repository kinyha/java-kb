package kb.modules.notifications;

import kb.modules.payments.PaymentReceipt;

public final class NotificationSender {
    public String message(PaymentReceipt receipt) { return receipt.caption(); }
}
