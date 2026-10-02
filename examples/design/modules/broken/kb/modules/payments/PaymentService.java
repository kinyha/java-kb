package kb.modules.payments;

import kb.modules.bank.BankClient;
import kb.modules.payments.internal.PaymentLedger;

public final class PaymentService {
    private final BankClient bank;
    private final PaymentLedger ledger = new PaymentLedger();

    public PaymentService(BankClient bank) { this.bank = bank; }
    public PaymentReceipt pay(String id) {
        return new PaymentReceipt(id, ledger.caption(id), bank.charge(id));
    }
    public static String bankReference(String id) { return "bank:" + id; }
}
