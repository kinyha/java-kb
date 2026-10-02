package kb.modules.bank;

import kb.modules.payments.PaymentService;

public final class BankClient {
    public String charge(String id) {
        // Удобный общий метод вернул зависимость из bank в payments.
        return PaymentService.bankReference(id);
    }
}
