package kb.modules.payments.internal;

// public нужен другому пакету внутри payments. Это ещё не API для соседних модулей.
public final class PaymentLedger {
    public String caption(String paymentId) { return "paid:" + paymentId; }
}
