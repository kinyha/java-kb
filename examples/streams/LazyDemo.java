import java.util.*;
import java.util.stream.*;

// Запуск: java LazyDemo.java   (JDK 21, без зависимостей)
public class LazyDemo {
    record Payment(String id, long amount, String currency) {}
    static final List<Payment> BATCH = List.of(
            new Payment("p-1", 500, "RUB"), new Payment("p-2", 90_000, "RUB"), new Payment("p-3", 120_000, "USD"),
            new Payment("p-4", 70_000, "RUB"), new Payment("p-5", 300, "EUR"));

    public static void main(String[] args) {
        System.out.println("== nothing runs without a terminal operation");
        Stream<Payment> pipeline = BATCH.stream().filter(p -> { System.out.println("   filter " + p.id()); return true; });
        System.out.println("pipeline built, no output above this line");

        System.out.println("== element by element, and only as many as needed");
        Optional<String> firstBig = BATCH.stream()
                .filter(p -> { System.out.println("   filter " + p.id()); return p.amount() > 50_000; })
                .map(p -> { System.out.println("   map    " + p.id()); return p.id(); })
                .findFirst();
        System.out.println("findFirst = " + firstBig.orElseThrow());

        System.out.println("== sorted is a barrier: it needs every element first");
        BATCH.stream()
                .peek(p -> System.out.println("   before sorted " + p.id()))
                .sorted(Comparator.comparingLong(Payment::amount).reversed())
                .limit(2)
                .forEach(p -> System.out.println("   after sorted  " + p.id()));

        System.out.println("== count() may skip the pipeline entirely");
        long n = BATCH.stream().peek(p -> System.out.println("   peek " + p.id())).count();
        System.out.println("count = " + n + " (size known, peek never ran)");
        long m = BATCH.stream().filter(p -> true).peek(p -> System.out.println("   peek " + p.id())).count();
        System.out.println("count after filter = " + m);

        System.out.println("== a stream is single-use");
        Stream<Payment> s = BATCH.stream();
        s.count();
        try { s.count(); } catch (IllegalStateException e) { System.out.println("second terminal op: " + e.getMessage()); }

        System.out.println("== map vs flatMap");
        List<List<String>> tagsPerPayment = List.of(List.of("card", "3ds"), List.of(), List.of("sbp"));
        System.out.println("map:     " + tagsPerPayment.stream().map(List::size).toList());
        System.out.println("flatMap: " + tagsPerPayment.stream().flatMap(List::stream).toList());

        System.out.println("== reduce: identity must be neutral");
        List<Long> amounts = BATCH.stream().map(Payment::amount).toList();
        System.out.println("sum, identity 0:          " + amounts.stream().reduce(0L, Long::sum));
        System.out.println("identity 100 sequential:  " + amounts.stream().reduce(100L, Long::sum));
        System.out.println("identity 100 parallel:    " + amounts.parallelStream().reduce(100L, Long::sum));

        System.out.println("== infinite sources need a short-circuit");
        System.out.println("retry delays: " + Stream.iterate(100L, d -> d * 2).limit(6).toList());
        System.out.println("takeWhile < 1000: " + Stream.iterate(100L, d -> d * 2).takeWhile(d -> d < 1000).toList());

        System.out.println("== toList() vs collect(Collectors.toList())");
        List<String> a = BATCH.stream().map(Payment::id).toList();
        List<String> b = BATCH.stream().map(Payment::id).collect(Collectors.toList());
        try { a.add("x"); } catch (UnsupportedOperationException e) { System.out.println("Stream.toList(): unmodifiable"); }
        b.add("x");
        System.out.println("Collectors.toList(): " + b.getClass().getSimpleName() + ", add worked");
    }
}
