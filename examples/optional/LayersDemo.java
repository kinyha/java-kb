import java.util.*;
import java.util.function.*;

// Запуск: java LayersDemo.java   (JDK 21, без зависимостей)
// Optional по слоям: репозиторий возвращает Optional, сервис решает, что значит отсутствие.
public class LayersDemo {
    record Payment(String id, long amount, String status) {}
    static class NotFound extends RuntimeException { NotFound(String m) { super(m); } }

    static final Map<String, Payment> PRIMARY = Map.of("p-1", new Payment("p-1", 500, "PAID"));
    static final Map<String, Payment> ARCHIVE = Map.of("p-0", new Payment("p-0", 900, "REFUNDED"));
    static int archiveCalls;

    // Репозиторий: отсутствие — нормальный результат поиска.
    static Optional<Payment> findById(String id) { return Optional.ofNullable(PRIMARY.get(id)); }
    static Optional<Payment> findInArchive(String id) { archiveCalls++; return Optional.ofNullable(ARCHIVE.get(id)); }

    // Сервис: здесь решается, ошибка ли это.
    static Payment get(String id) {
        return findById(id)
                .or(() -> findInArchive(id))                                  // запасной источник, лениво
                .orElseThrow(() -> new NotFound("payment " + id + " not found"));
    }
    static String statusText(String id) {
        return findById(id).filter(p -> p.status().equals("PAID")).map(p -> "оплачен " + p.amount()).orElse("не оплачен");
    }

    // Как устроен map внутри Optional: одна строка.
    static <T, R> Optional<R> map(Optional<T> o, Function<? super T, ? extends R> f) {
        return o.isEmpty() ? Optional.empty() : Optional.ofNullable(f.apply(o.get()));
    }

    public static void main(String[] args) {
        System.out.println("== repository returns Optional, service decides");
        for (String id : List.of("p-1", "p-0", "p-404")) {
            archiveCalls = 0;
            try { System.out.println(id + " -> " + get(id) + ", archive calls " + archiveCalls); }
            catch (NotFound e) { System.out.println(id + " -> NotFound: " + e.getMessage() + " (HTTP 404), archive calls " + archiveCalls); }
        }
        System.out.println("statusText(p-1) = " + statusText("p-1") + ", statusText(p-0) = " + statusText("p-0"));

        System.out.println("== map is one line");
        System.out.println(map(Optional.of("p-1"), String::toUpperCase) + " " + map(Optional.of("p-1"), s -> null) + " " + map(Optional.<String>empty(), String::length));

        System.out.println("== equals compares contents");
        System.out.println("Optional.of(\"p-1\").equals(Optional.of(new String(\"p-1\"))) = " + Optional.of("p-1").equals(Optional.of(new String("p-1"))));
    }
}
