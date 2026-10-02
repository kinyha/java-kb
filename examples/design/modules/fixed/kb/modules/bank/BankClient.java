package kb.modules.bank;

import kb.modules.payments.BankGateway;

public final class BankClient implements BankGateway {
    @Override public String charge(String id) { return "bank:" + id; }
}
