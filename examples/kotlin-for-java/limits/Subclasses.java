package pay;

class LoggingRefunds extends RefundService {
    @Override public String refund(long amount) { return "log + " + super.refund(amount); }
    @Override public String audit() { return "log audit"; }       // final в Kotlin
}

class LoggingPayments extends PaymentService { }                  // final class
