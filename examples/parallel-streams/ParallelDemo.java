import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

// Запуск: java -Xmx2g ParallelDemo.java   (JDK 21). Время — лучший из пяти прогонов; зависит от числа ядер.
public class ParallelDemo {
    static long sink;
    interface Job { long run(); }
    static double best(Job job) {
        double best = Double.MAX_VALUE;
        for (int r = 0; r < 5; r++) { long t0 = System.nanoTime(); sink += job.run(); best = Math.min(best, (System.nanoTime() - t0) / 1e6); }
        return best;
    }
    static void compare(String name, Job seq, Job par) {
        double s = best(seq), p = best(par);
        System.out.printf("%-38s sequential %8.2f ms, parallel %8.2f ms, x%.1f%n", name, s, p, s / p);
    }
    // «Тяжёлая» проверка платежа: много арифметики на элемент.
    static long score(long x) { double d = x; for (int i = 0; i < 10; i++) d = Math.sin(d) * 1000 + i; return d > 0 ? 1 : 0; }

    public static void main(String[] args) throws Exception {
        System.out.println("== the common pool");
        System.out.println("cores: " + Runtime.getRuntime().availableProcessors() + ", commonPool parallelism: " + ForkJoinPool.commonPool().getParallelism());
        Set<String> threads = ConcurrentHashMap.newKeySet();
        IntStream.range(0, 10_000).parallel().forEach(i -> threads.add(Thread.currentThread().getName().replaceAll("\\d+$", "N")));
        System.out.println("threads used: " + new TreeSet<>(threads));

        System.out.println("== when it helps and when it does not");
        long[] arr = LongStream.range(0, 1_000_000).toArray();
        List<Long> arrayList = Arrays.stream(arr).boxed().collect(Collectors.toCollection(ArrayList::new));
        List<Long> linked = new LinkedList<>(arrayList.subList(0, 200_000));
        List<Long> arraySmall = new ArrayList<>(arrayList.subList(0, 200_000));
        compare("heavy work, long[] 1M", () -> Arrays.stream(arr).map(ParallelDemo::score).sum(), () -> Arrays.stream(arr).parallel().map(ParallelDemo::score).sum());
        compare("cheap sum, long[] 1M", () -> Arrays.stream(arr).sum(), () -> Arrays.stream(arr).parallel().sum());
        long[] small = Arrays.copyOf(arr, 20_000);
        compare("cheap sum, long[] 20k", () -> Arrays.stream(small).sum(), () -> Arrays.stream(small).parallel().sum());
        compare("heavy work, ArrayList 200k", () -> arraySmall.stream().mapToLong(ParallelDemo::score).sum(), () -> arraySmall.parallelStream().mapToLong(ParallelDemo::score).sum());
        compare("heavy work, LinkedList 200k", () -> linked.stream().mapToLong(ParallelDemo::score).sum(), () -> linked.parallelStream().mapToLong(ParallelDemo::score).sum());
        List<Long> linkedBig = new LinkedList<>(arrayList);
        compare("cheap sum, ArrayList<Long> 1M", () -> arrayList.stream().mapToLong(Long::longValue).sum(), () -> arrayList.parallelStream().mapToLong(Long::longValue).sum());
        compare("cheap sum, LinkedList<Long> 1M", () -> linkedBig.stream().mapToLong(Long::longValue).sum(), () -> linkedBig.parallelStream().mapToLong(Long::longValue).sum());
        List<Long> tiny = arrayList.subList(0, 100);
        compare("cheap work, 100 elements", () -> tiny.stream().mapToLong(x -> x * 2).sum(), () -> tiny.parallelStream().mapToLong(x -> x * 2).sum());

        System.out.println("== order");
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        IntStream.range(0, 10).parallel().forEach(i -> { synchronized (a) { a.append(i); } });
        IntStream.range(0, 10).parallel().forEachOrdered(b::append);
        System.out.println("forEach: " + a + ", forEachOrdered: " + b + ", toList keeps order: " + IntStream.range(0, 10).parallel().boxed().toList());
        System.out.println("findFirst: " + IntStream.range(0, 1000).parallel().filter(i -> i % 7 == 3).findFirst().getAsInt()
                + ", findAny: may differ run to run");

        System.out.println("== shared mutable state");
        for (int round = 0; round < 3; round++) {
            List<Integer> unsafe = new ArrayList<>();
            String outcome;
            try { IntStream.range(0, 100_000).parallel().forEach(unsafe::add); outcome = "size " + unsafe.size(); }
            catch (ArrayIndexOutOfBoundsException e) { outcome = "ArrayIndexOutOfBoundsException"; }
            List<Integer> safe = IntStream.range(0, 100_000).parallel().boxed().toList();
            System.out.println("forEach(list::add): " + outcome + "; toList(): size " + safe.size());
        }

        System.out.println("== blocking calls starve the common pool");
        int p = ForkJoinPool.commonPool().getParallelism();
        long t0 = System.nanoTime();
        IntStream.range(0, p * 2).parallel().forEach(i -> sleep(200));          // «HTTP-вызовы» по 200 мс
        System.out.printf("%d blocking tasks of 200 ms in the common pool: %d ms%n", p * 2, (System.nanoTime() - t0) / 1_000_000);
        ForkJoinPool own = new ForkJoinPool(p * 2);
        t0 = System.nanoTime();
        own.submit(() -> IntStream.range(0, p * 2).parallel().forEach(i -> sleep(200))).get();
        System.out.printf("same in a dedicated ForkJoinPool(%d): %d ms%n", p * 2, (System.nanoTime() - t0) / 1_000_000);
        own.shutdown();
        try (var vt = Executors.newVirtualThreadPerTaskExecutor()) {
            t0 = System.nanoTime();
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < p * 2; i++) fs.add(vt.submit(() -> sleep(200)));
            for (Future<?> f : fs) f.get();
            System.out.printf("same with virtual threads: %d ms%n", (System.nanoTime() - t0) / 1_000_000);
        }
    }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
