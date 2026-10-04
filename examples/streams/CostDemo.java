import java.util.*;
import java.util.stream.*;

// Запуск: java -Xmx2g CostDemo.java   (JDK 21). Однопоточные замеры, лучший из восьми прогонов.
public class CostDemo {
    static long sink;
    interface Job { long run(); }
    static void measure(String name, Job job) {
        double best = Double.MAX_VALUE;
        for (int r = 0; r < 8; r++) { long t0 = System.nanoTime(); sink += job.run(); best = Math.min(best, (System.nanoTime() - t0) / 1e6); }
        System.out.printf("%-42s %7.1f ms%n", name, best);
    }

    public static void main(String[] args) {
        int n = 10_000_000;
        long[] amounts = new Random(3).longs(n, 0, 100_000).toArray();
        List<Long> boxed = Arrays.stream(amounts).boxed().collect(Collectors.toCollection(ArrayList::new));

        System.out.println("== sum of amounts > 50 000, 10M elements");
        measure("for loop over long[]", () -> { long s = 0; for (long a : amounts) if (a > 50_000) s += a; return s; });
        measure("LongStream over long[]", () -> Arrays.stream(amounts).filter(a -> a > 50_000).sum());
        measure("for loop over List<Long>", () -> { long s = 0; for (Long a : boxed) if (a > 50_000) s += a; return s; });
        measure("stream().filter().mapToLong().sum()", () -> boxed.stream().filter(a -> a > 50_000).mapToLong(Long::longValue).sum());
        measure("stream().filter().reduce(0L, Long::sum)", () -> boxed.stream().filter(a -> a > 50_000).reduce(0L, Long::sum));

        System.out.println("== a stream over 5 elements, called 1M times");
        List<Long> small = List.of(500L, 90_000L, 120_000L, 70_000L, 300L);
        measure("for loop", () -> { long t = 0; for (int i = 0; i < 1_000_000; i++) { long s = 0; for (long a : small) if (a > 50_000) s += a; t += s; } return t; });
        measure("stream", () -> { long t = 0; for (int i = 0; i < 1_000_000; i++) t += small.stream().filter(a -> a > 50_000).mapToLong(Long::longValue).sum(); return t; });
    }
}
