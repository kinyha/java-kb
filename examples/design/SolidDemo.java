import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Запуск: java SolidDemo.java   (JDK 21, без сборки)
public class SolidDemo {

    // ---------- OCP: новый способ оплаты — новый класс, сервис не меняется ----------
    record Payment(String id, String method, long cents) {}

    interface PaymentProcessor {
        String method();                 // Какой способ оплаты обрабатывает.
        String charge(Payment p);
    }

    static class CardProcessor implements PaymentProcessor {
        public String method() { return "card"; }
        public String charge(Payment p) { return "card: 3-D Secure, charged " + p.cents(); }
    }

    static class SbpProcessor implements PaymentProcessor {
        public String method() { return "sbp"; }
        public String charge(Payment p) { return "sbp: QR sent for " + p.cents(); }
    }

    // Знает только интерфейс; список реализаций в Spring пришёл бы как List<PaymentProcessor>.
    static class PaymentService {
        private final Map<String, PaymentProcessor> byMethod;

        PaymentService(List<PaymentProcessor> processors) {
            byMethod = processors.stream().collect(Collectors.toMap(PaymentProcessor::method, Function.identity()));
        }

        String pay(Payment p) {
            var processor = byMethod.get(p.method());
            if (processor == null) throw new IllegalArgumentException("unsupported method " + p.method());
            return processor.charge(p);
        }
    }

    // ---------- LSP: подтип компилируется, но ломает договор ----------
    interface RefundingProcessor extends PaymentProcessor {
        String refund(Payment p);        // Договор: вернуть деньги клиенту.
    }

    static class CardRefunds extends CardProcessor implements RefundingProcessor {
        public String refund(Payment p) { return "card: refunded " + p.cents(); }
    }

    static class WalletProcessor implements RefundingProcessor {
        public String method() { return "wallet"; }
        public String charge(Payment p) { return "wallet: debited " + p.cents(); }
        public String refund(Payment p) {
            throw new UnsupportedOperationException("wallet refunds are manual"); // Нарушение договора.
        }
    }

    // Код возвратов написан под договор интерфейса и ничего не знает о кошельке.
    static void refundAll(List<RefundingProcessor> processors, Payment p) {
        for (var processor : processors) {
            try {
                System.out.println("  " + processor.refund(p));
            } catch (UnsupportedOperationException e) {
                System.out.println("  " + processor.method() + ": " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("== OCP");
        var service = new PaymentService(List.of(new CardProcessor(), new SbpProcessor()));
        System.out.println(service.pay(new Payment("p-1", "card", 500)));
        System.out.println(service.pay(new Payment("p-2", "sbp", 700)));
        try {
            service.pay(new Payment("p-3", "crypto", 900));
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("== LSP: refunds");
        refundAll(List.of(new CardRefunds(), new WalletProcessor()), new Payment("p-4", "any", 300));

        System.out.println("== LSP in the JDK");
        List<String> methods = new ArrayList<>(List.of("card"));
        List<String> view = Collections.unmodifiableList(methods); // Тоже List.
        try {
            view.add("sbp");
        } catch (UnsupportedOperationException e) {
            System.out.println("unmodifiableList.add: " + e.getClass().getSimpleName());
        }
        methods.add("sbp"); // Меняем исходный список.
        System.out.println("view after source change: " + view);
        System.out.println("List.copyOf after change: " + List.copyOf(methods).getClass().getSimpleName()
                + " " + List.copyOf(methods));
    }
}
