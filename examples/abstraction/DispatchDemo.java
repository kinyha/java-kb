import java.util.*;

// Запуск: java DispatchDemo.java   (JDK 21, без зависимостей)
public class DispatchDemo {
    interface Gateway {
        String pay(long amount);
        default String name() { return "gateway"; }
        default String payLogged(long amount) { return log(name() + " pays " + amount) + " -> " + pay(amount); }  // default через абстрактный
        private String log(String s) { return "[" + s + "]"; }                                                  // private-метод интерфейса
    }
    interface CardGateway extends Gateway { default String name() { return "card"; } }

    static class BaseGateway { public String name() { return "base-class"; } }

    // Правило 1: метод класса важнее default из интерфейса.
    static class Sbp extends BaseGateway implements Gateway { public String pay(long a) { return "sbp ok"; } }
    // Правило 2: более конкретный интерфейс важнее.
    static class Visa implements Gateway, CardGateway { public String pay(long a) { return "visa ok"; } }

    // Абстрактный класс: общее состояние, конструктор и шаблонный метод.
    static abstract class RetryingGateway implements Gateway {
        private final int attempts;
        private int calls;
        protected RetryingGateway(int attempts) { this.attempts = attempts; }
        public final String pay(long amount) {
            for (int i = 1; ; i++) {
                calls++;
                try { return doPay(amount) + " after " + i; }
                catch (IllegalStateException e) { if (i == attempts) return "failed after " + i; }
            }
        }
        protected abstract String doPay(long amount);        // точка расширения
        int calls() { return calls; }
    }
    static class FlakyAcquirer extends RetryingGateway {
        private int n;
        FlakyAcquirer() { super(3); }
        protected String doPay(long amount) { if (++n < 3) throw new IllegalStateException("timeout"); return "acquirer ok"; }
    }

    public static void main(String[] args) {
        System.out.println("== which name() runs");
        System.out.println("Sbp  (class + interface default): " + new Sbp().name());
        System.out.println("Visa (Gateway + CardGateway):     " + new Visa().name());
        System.out.println("== default built on an abstract method");
        System.out.println(new Visa().payLogged(500));
        System.out.println("== abstract class with state and a template method");
        FlakyAcquirer f = new FlakyAcquirer();
        System.out.println(f.pay(700) + ", calls = " + f.calls());
        Gateway lambda = amount -> "lambda ok";                 // интерфейс с одним абстрактным методом
        System.out.println("lambda as Gateway: " + lambda.payLogged(1));
    }
}
