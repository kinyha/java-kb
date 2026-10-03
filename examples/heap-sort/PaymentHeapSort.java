import java.util.Arrays;
import java.util.Comparator;

public class PaymentHeapSort {
    record Payment(long id, long amount) {
        @Override public String toString() { return id + ":" + amount; }
    }
    static final Comparator<Payment> ORDER = (p, q) -> {
        int byAmount = Long.compare(p.amount(), q.amount()); // не вычитаем long
        return byAmount != 0 ? byAmount : Long.compare(p.id(), q.id());
    };
    // BEGIN solution
    static void sort(Payment[] a) {
        for (int root = a.length / 2 - 1; root >= 0; root--) sink(a, root, a.length);
        for (int size = a.length; size > 1;) {
            Payment t = a[0]; a[0] = a[--size]; a[size] = t; // максимум пары ключей вправо
            sink(a, 0, size);
        }
    }
    static void sink(Payment[] a, int root, int size) {
        while (root < size / 2) {
            int child = 2 * root + 1;
            if (child + 1 < size && ORDER.compare(a[child], a[child + 1]) < 0) child++; // больший ребёнок
            if (ORDER.compare(a[root], a[child]) >= 0) return;
            Payment t = a[root]; a[root] = a[child]; a[child] = t;
            root = child;
        }
    }
    // END solution
    static void check(Payment[] input, Payment[] want) {
        sort(input);
        if (!Arrays.equals(input, want)) throw new AssertionError(Arrays.toString(input));
        System.out.println("OK " + Arrays.toString(input));
    }
    public static void main(String[] args) {
        check(new Payment[]{new Payment(20, 100), new Payment(10, 100), new Payment(30, 200)},
                new Payment[]{new Payment(10, 100), new Payment(20, 100), new Payment(30, 200)});
        check(new Payment[]{new Payment(2, Long.MAX_VALUE), new Payment(1, Long.MIN_VALUE), new Payment(3, 0)},
                new Payment[]{new Payment(1, Long.MIN_VALUE), new Payment(3, 0), new Payment(2, Long.MAX_VALUE)});
        check(new Payment[]{new Payment(9, 500)}, new Payment[]{new Payment(9, 500)});
        check(new Payment[]{}, new Payment[]{});
    }
}
