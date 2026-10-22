package pay;

// Как Kotlin-код выглядит для Java-вызывающего.
public class JavaView {
    public static void main(String[] args) {
        Payment p = new Payment("p-1", "NEW");      // аргумент по умолчанию Java не видит: передаём оба
        p.setStatus("PAID");                        // var status → setStatus
        System.out.println(p.getId() + " " + p.getStatus() + " final=" + p.isFinal());

        Payment a = Payment.Companion.of("p-2");    // без @JvmStatic — через объект Companion
        Payment b = Payment.draft("p-3");           // с @JvmStatic — как обычный static
        System.out.println(a.getStatus() + " " + b.getStatus() + " max=" + Payment.MAX_AMOUNT);

        FeePolicy.INSTANCE.setPercent(20);          // object — это класс с полем INSTANCE
        System.out.println(FeePolicy.INSTANCE.fee(10_000) + " " + PaymentKt.formatAmount(10_150));
    }
}
