import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// Запуск: java HexagonalDemo.java   (JDK 21)
// Ядро (Core) ничего не знает об адаптерах; адаптеры подключаются снаружи.
public class HexagonalDemo {

    // ================= ЯДРО: модель, сценарий, порты =================
    interface Core {}    // Метка классов ядра.
    interface Adapter {} // Метка адаптеров.

    record Money(long cents) implements Core {
        Money { if (cents <= 0) throw new IllegalArgumentException("amount must be positive"); }
    }

    enum Status implements Core { APPROVED, DECLINED }

    record Payment(String id, Money amount, Status status) implements Core {}

    // Входящий порт: что приложение умеет делать.
    interface PayUseCase extends Core { Payment pay(String id, long cents); }

    // Исходящие порты: что ядру нужно от мира. Владелец — ядро.
    interface PaymentStore extends Core { void save(Payment p); List<Payment> all(); }
    interface Bank extends Core { boolean charge(String id, Money amount); }

    // Сценарий: бизнес-правило живёт здесь и только здесь.
    static final class PayService implements PayUseCase, Core {
        private final Bank bank;
        private final PaymentStore store;
        PayService(Bank bank, PaymentStore store) { this.bank = bank; this.store = store; }

        public Payment pay(String id, long cents) {
            Money amount = new Money(cents);
            Status status = bank.charge(id, amount) ? Status.APPROVED : Status.DECLINED;
            Payment p = new Payment(id, amount, status);
            store.save(p);
            return p;
        }
    }

    // Нарушение для проверки: отчёт ядра знает конкретное хранилище.
    static final class DailyReport implements Core {
        private final InMemoryStore store; // Ссылка из ядра на адаптер.
        DailyReport(InMemoryStore store) { this.store = store; }
        long total() { return store.all().stream().mapToLong(p -> p.amount().cents()).sum(); }
    }

    // ================= АДАПТЕРЫ =================
    // Входящий адаптер: «HTTP» в вызов сценария и обратно.
    static final class PaymentHttpAdapter implements Adapter {
        private final PayUseCase useCase;
        PaymentHttpAdapter(PayUseCase useCase) { this.useCase = useCase; }

        String post(String json) { // {"id":"p-1","cents":500}
            String id = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            long cents = Long.parseLong(json.replaceAll(".*\"cents\":(-?\\d+).*", "$1"));
            try {
                Payment p = useCase.pay(id, cents);
                return "201 {\"id\":\"" + p.id() + "\",\"status\":\"" + p.status() + "\"}";
            } catch (IllegalArgumentException e) {
                return "400 {\"error\":\"" + e.getMessage() + "\"}";
            }
        }
    }

    // Исходящие адаптеры: хранилище и банк.
    static final class InMemoryStore implements PaymentStore, Adapter {
        private final List<Payment> rows = new ArrayList<>();
        public void save(Payment p) { rows.add(p); }
        public List<Payment> all() { return rows; }
    }

    static final class LimitBank implements Bank, Adapter { // Тестовый банк: отказ выше лимита.
        public boolean charge(String id, Money amount) { return amount.cents() <= 10_000; }
    }

    static final class SandboxBank implements Bank, Adapter { // «Песочница» банка: считает вызовы.
        final Map<String, Long> charged = new HashMap<>();
        public boolean charge(String id, Money amount) { charged.put(id, amount.cents()); return true; }
    }

    // Проверка правила зависимостей: типы полей и параметров классов ядра.
    static List<String> coreDependenciesOnAdapters() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : HexagonalDemo.class.getDeclaredClasses()) {
            if (!Core.class.isAssignableFrom(c) || Adapter.class.isAssignableFrom(c)) continue;
            Stream<Class<?>> used = Stream.concat(
                    Arrays.stream(c.getDeclaredFields()).map(f -> f.getType()),
                    Arrays.stream(c.getDeclaredMethods()).flatMap((Method m) ->
                            Stream.concat(Stream.of(m.getReturnType()), Arrays.stream(m.getParameterTypes()))));
            used.filter(Adapter.class::isAssignableFrom)
                .forEach(t -> violations.add(c.getSimpleName() + " -> " + t.getSimpleName()));
        }
        return violations;
    }

    public static void main(String[] args) {
        System.out.println("== same core, test adapters");
        var store = new InMemoryStore();
        var http = new PaymentHttpAdapter(new PayService(new LimitBank(), store));
        System.out.println(http.post("{\"id\":\"p-1\",\"cents\":500}"));
        System.out.println(http.post("{\"id\":\"p-2\",\"cents\":25000}"));
        System.out.println(http.post("{\"id\":\"p-3\",\"cents\":-5}"));
        System.out.println("stored: " + store.all().stream().map(p -> p.id() + "=" + p.status()).toList());

        System.out.println("== same core, another bank adapter");
        var sandbox = new SandboxBank();
        var useCase = new PayService(sandbox, new InMemoryStore());
        System.out.println(useCase.pay("p-4", 25000).status() + ", bank saw " + sandbox.charged);

        System.out.println("== dependency rule check");
        var v = coreDependenciesOnAdapters();
        System.out.println(v.isEmpty() ? "core -> adapters: none" : "violations: " + v.stream().distinct().toList());
    }
}
