package kb.modules.payments;

import kb.modules.payments.internal.PaymentLedger;

public final class PaymentService {
    private final BankGateway bank;
    private final PaymentLedger ledger = new PaymentLedger();

    public PaymentService(BankGateway bank) { this.bank = bank; }
    public PaymentReceipt pay(String id) {
        return new PaymentReceipt(id, ledger.caption(id), bank.charge(id));
    }
}
