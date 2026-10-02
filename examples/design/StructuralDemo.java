import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.List;

// Запуск: java StructuralDemo.java   (JDK 21)
public class StructuralDemo {

    // Интерфейс, которым пользуется платёжный сервис.
    interface BankGateway {
        String charge(String id, long cents);
    }

    // ---------- Adapter: SDK банка с чужим API ----------
    static class LegacyBankSdk { // Принимает рубли в double и бросает свои исключения.
        int attempts;
        String pay(String reference, double rubles) {
            attempts++;
            if (attempts == 1) throw new IllegalStateException("timeout");
            return "OK " + reference + " " + rubles;
        }
    }

    static class LegacyBankAdapter implements BankGateway {
        private final LegacyBankSdk sdk;
        LegacyBankAdapter(LegacyBankSdk sdk) { this.sdk = sdk; }

        public String charge(String id, long cents) {
            // Копейки → рубли без потерь double в нашей части кода.
            double rubles = BigDecimal.valueOf(cents).movePointLeft(2).doubleValue();
            return sdk.pay(id, rubles);
        }
    }

    // ---------- Decorator: одно поведение на класс ----------
    static class Logging implements BankGateway {
        private final BankGateway next;
        Logging(BankGateway next) { this.next = next; }
        public String charge(String id, long cents) {
            System.out.println("  log: charge " + id);
            return next.charge(id, cents);
        }
    }

    static class Retrying implements BankGateway {
        private final BankGateway next;
        Retrying(BankGateway next) { this.next = next; }
        public String charge(String id, long cents) {
            try {
                return next.charge(id, cents);
            } catch (IllegalStateException e) {
                System.out.println("  retry after: " + e.getMessage());
                return next.charge(id, cents);
            }
        }
    }

    // ---------- Composite: правило комиссии из правил ----------
    interface FeeRule { long fee(long cents); String describe(); }

    record Percent(String name, int basisPoints) implements FeeRule {
        public long fee(long cents) { return cents * basisPoints / 10_000; }
        public String describe() { return name; }
    }

    record Fixed(String name, long cents) implements FeeRule {
        public long fee(long amount) { return cents; }
        public String describe() { return name; }
    }

    record AllOf(String name, List<FeeRule> rules) implements FeeRule { // Сумма вложенных правил.
        public long fee(long cents) { return rules.stream().mapToLong(r -> r.fee(cents)).sum(); }
        public String describe() { return name + rules.stream().map(FeeRule::describe).toList(); }
    }

    public static void main(String[] args) {
        System.out.println("== adapter + decorators: Retrying(Logging(adapter))");
        BankGateway a = new Retrying(new Logging(new LegacyBankAdapter(new LegacyBankSdk())));
        System.out.println("  result: " + a.charge("p-1", 12_345));

        System.out.println("== decorators reversed: Logging(Retrying(adapter))");
        BankGateway b = new Logging(new Retrying(new LegacyBankAdapter(new LegacyBankSdk())));
        System.out.println("  result: " + b.charge("p-2", 12_345));

        System.out.println("== dynamic proxy");
        BankGateway target = (id, cents) -> "charged " + id;
        BankGateway timed = (BankGateway) Proxy.newProxyInstance(
                BankGateway.class.getClassLoader(), new Class<?>[] {BankGateway.class},
                (proxy, method, params) -> {
                    System.out.println("  intercepted " + method.getName()); // До вызова цели.
                    return method.invoke(target, params);                   // Вызов настоящего объекта.
                });
        System.out.println("  proxy class: " + timed.getClass().getName());
        System.out.println("  result: " + timed.charge("p-3", 100));
        String text = timed.toString(); // Тоже идёт через обработчик.
        System.out.println("  toString is target's: " + text.startsWith("StructuralDemo$$Lambda"));

        System.out.println("== composite");
        FeeRule card = new AllOf("card", List.of(
                new Percent("acquiring 1.5%", 150),
                new AllOf("bank", List.of(new Fixed("fixed 10", 10), new Percent("3ds 0.1%", 10)))));
        System.out.println("  " + card.describe());
        System.out.println("  fee for 10000: " + card.fee(10_000));
    }
}
