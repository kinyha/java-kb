import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;

public class ParallelSortDemo {
    record Payment(int id, int amount) {}
    static void run(int size) {
        Set<String> threads = new ConcurrentSkipListSet<>();
        var random = new SplittableRandom(42);
        Payment[] a = new Payment[size];
        for (int i = 0; i < size; i++) a[i] = new Payment(i, random.nextInt(10));
        Comparator<Payment> comparator = (x, y) -> {
            threads.add(Thread.currentThread().getName());
            return Integer.compare(x.amount, y.amount);
        };
        Payment[] explicitOrder = a.clone();
        Arrays.parallelSort(a, comparator); // Сравниваем только суммы.
        boolean ordered = true, stable = true;
        for (int i = 1; i < size; i++) {
            ordered &= a[i - 1].amount <= a[i].amount;
            if (a[i - 1].amount == a[i].amount) stable &= a[i - 1].id < a[i].id;
        }
        if (!ordered) throw new AssertionError("order");
        System.out.println("n=" + size + " ordered=" + ordered + " stable=" + stable);
        System.out.println("comparison threads=" + threads);
        Arrays.parallelSort(explicitOrder, Comparator.comparingInt(Payment::amount)
                .thenComparingInt(Payment::id)); // Теперь порядок одинаковых сумм задан явно.
        boolean idsAscending = true;
        for (int i = 1; i < size; i++) {
            if (explicitOrder[i - 1].amount == explicitOrder[i].amount)
                idsAscending &= explicitOrder[i - 1].id < explicitOrder[i].id;
        }
        if (!idsAscending) throw new AssertionError("explicit tie key");
        System.out.println("explicit id order=" + idsAscending);
    }
    public static void main(String[] args) {
        System.out.println("common parallelism=" + ForkJoinPool.getCommonPoolParallelism());
        run(8192);
        run(32768);
    }
}
