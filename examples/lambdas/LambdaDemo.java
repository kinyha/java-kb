import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.*;

// Запуск: java LambdaDemo.java   (JDK 21, без зависимостей)
public class LambdaDemo {
    record Payment(String id, long amount, String currency) {}

    private final long feeRate = 15;
    long fee(Payment p) { return p.amount() * feeRate / 1000; }

    Supplier<ToLongFunction<Payment>> nonCapturing() { return () -> p -> p.amount() / 100; }

    public static void main(String[] args) throws Exception {
        List<Payment> batch = List.of(new Payment("p-1", 10_000, "RUB"), new Payment("p-2", 500, "USD"), new Payment("p-3", 70_000, "RUB"));

        System.out.println("== from anonymous class to method reference");
        Comparator<Payment> c1 = new Comparator<>() { public int compare(Payment a, Payment b) { return Long.compare(a.amount(), b.amount()); } };
        Comparator<Payment> c2 = (a, b) -> Long.compare(a.amount(), b.amount());
        Comparator<Payment> c3 = Comparator.comparingLong(Payment::amount);
        System.out.println(sortedIds(batch, c1) + " " + sortedIds(batch, c2) + " " + sortedIds(batch, c3.reversed()));

        System.out.println("== one object or a new one per evaluation?");
        Runnable[] r = new Runnable[2]; Supplier<?>[] cap = new Supplier<?>[2];
        for (int i = 0; i < 2; i++) {
            int n = i;
            r[i] = () -> {};                                       // ничего не захватывает
            cap[i] = () -> n;                                      // захватывает n
        }
        System.out.println("non-capturing: same instance " + (r[0] == r[1]) + ", capturing: same instance " + (cap[0] == cap[1]));
        System.out.println("lambda class: " + r[0].getClass().getSimpleName().replaceAll("0x[0-9a-f]+", "0x…") + ", synthetic/hidden: " + r[0].getClass().isHidden());

        System.out.println("== method reference kinds");
        LambdaDemo service = new LambdaDemo();
        Function<String, Integer> parse = Integer::parseInt;                    // статический метод
        ToLongFunction<Payment> bound = service::fee;                           // метод конкретного объекта
        Function<Payment, String> unbound = Payment::currency;                  // метод аргумента
        BiFunction<String, Long, Payment> ctor = (id, amt) -> new Payment(id, amt, "RUB");
        Function<String, StringBuilder> newSb = StringBuilder::new;             // конструктор
        System.out.println(parse.apply("42") + " " + bound.applyAsLong(batch.get(0)) + " " + unbound.apply(batch.get(1)) + " " + ctor.apply("p-9", 5L).id() + " " + newSb.apply("x").append("y"));

        System.out.println("== bound reference evaluates the receiver once");
        int calls0 = rateCalls;
        Supplier<String> viaRef = currentRate()::toString;                  // currentRate() вызван сейчас, один раз
        int calls1 = rateCalls;
        for (int i = 0; i < 3; i++) viaRef.get();
        int calls2 = rateCalls;
        Supplier<String> viaLambda = () -> currentRate().toString();        // currentRate() на каждый get()
        for (int i = 0; i < 3; i++) viaLambda.get();
        System.out.println("currentRate()::toString  calls at creation " + (calls1 - calls0) + ", during 3 get() " + (calls2 - calls1));
        System.out.println("() -> currentRate()...   calls at creation 0, during 3 get() " + (rateCalls - calls2));
        String nothing = null;
        try { Supplier<Integer> len = nothing::length; }
        catch (NullPointerException e) { System.out.println("null::length -> NPE when the reference is created"); }
        Supplier<Integer> lazy = () -> nothing.length();
        System.out.println("() -> null.length() created fine, fails only on get()");

        System.out.println("== primitive specializations avoid boxing");
        int[] amounts = new Random(7).ints(10_000_000, 0, 1_000_000).toArray();
        List<Integer> boxedAmounts = Arrays.stream(amounts).boxed().toList();
        Function<Integer, Integer> boxedFee = x -> x * 15 / 1000;
        IntUnaryOperator primFee = x -> x * 15 / 1000;
        for (int round = 0; round < 3; round++) {
            long t0 = System.nanoTime(); List<Integer> fees = new ArrayList<>(amounts.length); for (Integer a : boxedAmounts) fees.add(boxedFee.apply(a));
            long t1 = System.nanoTime(); int[] pf = new int[amounts.length]; for (int i = 0; i < amounts.length; i++) pf[i] = primFee.applyAsInt(amounts[i]);
            long t2 = System.nanoTime();
            if (round == 2) System.out.printf("10M fees: List<Integer> + Function %d ms, int[] + IntUnaryOperator %d ms (%d %d)%n", (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, fees.get(5), pf[5]);
        }

        System.out.println("== checked exceptions do not fit java.util.function");
        List<String> files = List.of("a.csv", "missing.csv");
        try { files.forEach(unchecked(LambdaDemo::read)); }
        catch (UncheckedIOException e) { System.out.println("wrapped: " + e.getCause().getMessage()); }

        System.out.println("== stack trace of a lambda");
        try { batch.forEach(p -> { if (p.amount() > 50_000) throw new IllegalStateException("limit"); }); }
        catch (IllegalStateException e) {
            for (StackTraceElement el : e.getStackTrace()) if (el.getClassName().contains("LambdaDemo") || el.getMethodName().equals("forEach"))
                System.out.println("   at " + el.getClassName().replaceAll("\\$\\$Lambda.*", "$$Lambda") + "." + el.getMethodName());
        }
    }

    static int rateCalls;
    static StringBuilder currentRate() { rateCalls++; return new StringBuilder("1.5%"); }

    static String read(String name) throws IOException {
        if (name.startsWith("missing")) throw new IOException("no such file: " + name);
        return name;
    }
    interface IOFunction<T, R> { R apply(T t) throws IOException; }
    static <T> Consumer<T> unchecked(IOFunction<T, ?> f) {
        return t -> { try { f.apply(t); } catch (IOException e) { throw new UncheckedIOException(e); } };
    }
    static List<String> sortedIds(List<Payment> l, Comparator<Payment> c) { return l.stream().sorted(c).map(Payment::id).toList(); }
}
