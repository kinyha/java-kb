import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

// Запуск: java BehavioralDemo.java   (JDK 21)
public class BehavioralDemo {

    record Payment(String id, long cents, String country, String card) {}

    // ---------- Chain of responsibility: проверки до списания ----------
    interface Check { Optional<String> reject(Payment p); } // empty — пропустить дальше.

    static List<String> calls = new ArrayList<>();

    static Check named(String name, Check c) {
        return p -> { calls.add(name); return c.reject(p); };
    }

    static Optional<String> runChain(List<Check> chain, Payment p) {
        for (Check c : chain) {
            var r = c.reject(p);
            if (r.isPresent()) return r; // Первый отказ останавливает цепочку.
        }
        return Optional.empty();
    }

    // ---------- Template method: шаги интеграции с банком ----------
    abstract static class BankIntegration {
        BankIntegration() {
            System.out.println("  constructor sees endpoint=" + endpoint()); // Вызов переопределяемого метода.
        }
        final String charge(Payment p) {            // Шаблон: порядок шагов фиксирован.
            return sign(endpoint() + "/charge?" + p.id());
        }
        abstract String endpoint();                  // Шаг, который задаёт подкласс.
        String sign(String request) { return request + "&sig=sha256"; }
    }

    static class TinkoffIntegration extends BankIntegration {
        private final String endpoint;               // Значение придёт из настроек.
        TinkoffIntegration(String endpoint) {
            super();                                 // Сначала конструктор родителя...
            this.endpoint = endpoint;                // ...и только потом присваивание.
        }
        String endpoint() { return endpoint; }
    }

    static class ConstantIntegration extends BankIntegration {
        private final String endpoint = "https://securepay.tinkoff.ru"; // Константа времени компиляции.
        String endpoint() { return endpoint; }
    }

    // ---------- Observer: подписчики события ----------
    interface PaymentListener { void paid(Payment p); }

    // ---------- Command: операции как объекты с отменой ----------
    interface Command { void execute(); void undo(); String name(); }

    // ---------- State: допустимые переходы платежа ----------
    enum Status {
        NEW, AUTHORIZED, CAPTURED, REFUNDED, CANCELLED;

        static final Map<Status, List<Status>> NEXT = Map.of(
                NEW, List.of(AUTHORIZED, CANCELLED),
                AUTHORIZED, List.of(CAPTURED, CANCELLED),
                CAPTURED, List.of(REFUNDED),
                REFUNDED, List.of(),
                CANCELLED, List.of());

        Status to(Status next) {
            if (!NEXT.get(this).contains(next)) {
                throw new IllegalStateException(this + " -> " + next + " is not allowed");
            }
            return next;
        }
    }

    public static void main(String[] args) {
        System.out.println("== chain of responsibility");
        Check amount = named("amount", p -> p.cents() > 0 ? Optional.empty() : Optional.of("non-positive amount"));
        Check country = named("country", p -> p.country().equals("XX") ? Optional.of("sanctioned country") : Optional.empty());
        Check fraud = named("fraud(remote, 120 ms)", p -> Optional.empty()); // Дорогая проверка.
        var bad = new Payment("p-1", 500, "XX", "4111");
        calls.clear();
        System.out.println("  cheap first: " + runChain(List.of(amount, country, fraud), bad).orElse("ok") + ", calls=" + calls);
        calls.clear();
        System.out.println("  fraud first: " + runChain(List.of(fraud, amount, country), bad).orElse("ok") + ", calls=" + calls);

        System.out.println("== template method");
        var tinkoff = new TinkoffIntegration("https://securepay.tinkoff.ru");
        System.out.println("  " + tinkoff.charge(new Payment("p-2", 100, "RU", "4111")));
        new ConstantIntegration(); // Литерал в final-поле компилятор подставил в метод.

        System.out.println("== observer");
        List<PaymentListener> listeners = new ArrayList<>();
        listeners.add(p -> System.out.println("  ledger: recorded " + p.id()));
        listeners.add(p -> { throw new IllegalStateException("sms gateway down"); });
        listeners.add(p -> System.out.println("  analytics: counted " + p.id()));
        try {
            for (var l : listeners) l.paid(new Payment("p-3", 100, "RU", "4111"));
        } catch (IllegalStateException e) {
            System.out.println("  publish failed: " + e.getMessage() + " (analytics never ran)");
        }
        List<PaymentListener> once = new ArrayList<>();
        once.add(new PaymentListener() {
            public void paid(Payment p) { once.remove(this); } // Отписаться после первого события.
        });
        once.add(p -> System.out.println("  second listener got " + p.id()));
        once.add(p -> System.out.println("  third listener got " + p.id()));
        try {
            for (var l : once) l.paid(new Payment("p-4", 100, "RU", "4111"));
            System.out.println("  ArrayList publish finished without errors");
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("  unsubscribe during publish: " + e.getClass().getSimpleName());
        }
        List<PaymentListener> safe = new CopyOnWriteArrayList<>();
        safe.add(new PaymentListener() { public void paid(Payment p) { safe.remove(this); } });
        safe.add(p -> System.out.println("  second listener got " + p.id()));
        safe.add(p -> System.out.println("  third listener got " + p.id()));
        for (var l : safe) l.paid(new Payment("p-5", 100, "RU", "4111"));
        System.out.println("  CopyOnWriteArrayList: listeners left=" + safe.size());

        System.out.println("== command");
        long[] balance = {1000};
        Deque<Command> history = new ArrayDeque<>();
        Command charge = new Command() {
            public void execute() { balance[0] -= 300; }
            public void undo() { balance[0] += 300; }
            public String name() { return "charge 300"; }
        };
        Command fee = new Command() {
            public void execute() { balance[0] -= 5; }
            public void undo() { balance[0] += 5; }
            public String name() { return "fee 5"; }
        };
        for (var c : List.of(charge, fee)) { c.execute(); history.push(c); }
        System.out.println("  after commands: balance=" + balance[0] + ", log=" + history.stream().map(Command::name).toList());
        while (!history.isEmpty()) history.pop().undo(); // Отмена в обратном порядке.
        System.out.println("  after undo all: balance=" + balance[0]);

        System.out.println("== state");
        Status s = Status.NEW.to(Status.AUTHORIZED).to(Status.CAPTURED);
        System.out.println("  " + s);
        try {
            s.to(Status.CANCELLED); // Списанный платёж нельзя отменить — только вернуть.
        } catch (IllegalStateException e) {
            System.out.println("  " + e.getMessage());
        }
    }
}
