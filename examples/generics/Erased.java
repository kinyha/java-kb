import java.util.List;

// Компилируется javac и разбирается javap: что остаётся от параметров типа в байткоде.
public class Erased {
    record Payment(long amount) {}

    static long first(List<Payment> batch) {
        return batch.get(0).amount();          // get возвращает Object; компилятор вставляет checkcast
    }

    static <T extends Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;    // T стирается до своей границы — Comparable
    }
}
