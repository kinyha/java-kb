import java.util.*;
import java.util.function.*;

// Запуск: java -Xmx1g CompareDemo.java   (JDK 21, без зависимостей)
// Однопоточные замеры: лучший из нескольких прогонов после прогрева. Числа зависят от машины, важны отношения.
public class CompareDemo {
    static final int N = 100_000;
    static long sink;

    static double best(Supplier<List<Integer>> make, Consumer<List<Integer>> op) {
        double best = Double.MAX_VALUE;
        for (int r = 0; r < 7; r++) {
            List<Integer> l = make.get();
            long t0 = System.nanoTime();
            op.accept(l);
            best = Math.min(best, (System.nanoTime() - t0) / 1e6);
        }
        return best;
    }
    static List<Integer> fill(List<Integer> l) { for (int i = 0; i < N; i++) l.add(i); return l; }

    static void row(String name, Consumer<List<Integer>> op) {
        double a = best(() -> fill(new ArrayList<>()), op);
        double b = best(() -> fill(new LinkedList<>()), op);
        System.out.printf("%-34s ArrayList %8.2f ms   LinkedList %8.2f ms%n", name, a, b);
    }

    static long usedAfterGc() {
        for (int i = 0; i < 3; i++) System.gc();
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    public static void main(String[] args) {
        System.out.println("== time, N = 100 000 elements");
        row("iterate with for-each", l -> { long s = 0; for (int x : l) s += x; sink += s; });
        row("get(i) for 10 000 random i", l -> { Random r = new Random(1); long s = 0; for (int i = 0; i < 10_000; i++) s += l.get(r.nextInt(N)); sink += s; });
        row("add(size/2, x) 10 000 times", l -> { for (int i = 0; i < 10_000; i++) l.add(l.size() / 2, i); });
        row("ListIterator.add in middle 10 000", l -> { ListIterator<Integer> it = l.listIterator(N / 2); for (int i = 0; i < 10_000; i++) it.add(i); });
        row("add(0, x) 10 000 times", l -> { for (int i = 0; i < 10_000; i++) l.add(0, i); });

        System.out.println("== memory per element, 1 000 000 references to one shared object");
        Integer shared = 42;
        for (String kind : List.of("ArrayList (trimmed)", "LinkedList")) {
            long before = usedAfterGc();
            List<Integer> l = kind.startsWith("Array") ? new ArrayList<>() : new LinkedList<>();
            for (int i = 0; i < 1_000_000; i++) l.add(shared);
            if (l instanceof ArrayList<Integer> al) al.trimToSize();
            long after = usedAfterGc();
            System.out.printf("%-20s %.1f bytes per element%n", kind, (after - before) / 1e6);
            sink += l.size();
        }
    }
}
