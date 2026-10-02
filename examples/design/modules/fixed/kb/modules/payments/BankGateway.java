package kb.modules.payments;

// Платежи определяют нужную им операцию. Реализация банка находится снаружи.
public interface BankGateway {
    String charge(String paymentId);
}
