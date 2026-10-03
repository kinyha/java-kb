import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SortingDemo {
    record Payment(String id, int amount) {
        @Override public String toString() { return id + ":" + amount; }
    }
    static Payment[] fixture() {
        return new Payment[]{new Payment("C", 200), new Payment("A", 100),
                new Payment("B", 200), new Payment("D", 100)};
    }
    static void report(String name, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException e) { System.out.println(name + ": " + e.getClass().getSimpleName()); }
    }
    public static void main(String[] args) {
        var byAmount = Comparator.comparingInt(Payment::amount);
        System.out.println("[stable object order]");
        Payment[] a = fixture();
        Arrays.sort(a, byAmount);
        System.out.println("stable: " + Arrays.toString(a));
        a = fixture();
        Arrays.sort(a, byAmount.thenComparing(Payment::id));
        System.out.println("tie key: " + Arrays.toString(a));

        System.out.println("[mutation and views]");
        Payment[] original = fixture();
        List<Payment> view = Arrays.asList(original);
        view.sort(byAmount);
        System.out.println("array after view.sort: " + Arrays.toString(original));
        report("view.add", () -> view.add(new Payment("E", 50)));
        List<Payment> immutable = List.of(fixture());
        report("List.of.sort", () -> immutable.sort(byAmount));
        List<Payment> copy = new ArrayList<>(immutable);
        copy.sort(byAmount);
        System.out.println("sorted copy: " + copy);
        System.out.println("original list: " + immutable);

        System.out.println("[range and comparator errors]");
        int[] amounts = {999, 3, 1, 2, -999};
        Arrays.sort(amounts, 1, 4);
        System.out.println("range: " + Arrays.toString(amounts));
        report("from>to", () -> Arrays.sort(amounts, 4, 1));
        report("from<0", () -> Arrays.sort(amounts, -1, 2));
        report("natural Payment", () -> Arrays.sort(fixture()));
        System.out.println("subtract MAX-(-1): " + (Integer.MAX_VALUE - (-1)));
        System.out.println("compare MAX,-1: " + Integer.compare(Integer.MAX_VALUE, -1));

        System.out.println("[descending and null]");
        Comparator<Payment> descending = Comparator.nullsLast(
                byAmount.reversed().thenComparing(Payment::id));
        Payment[] nullable = {new Payment("B", 100), null,
                new Payment("A", 100), new Payment("C", 200)};
        Arrays.sort(nullable, descending);
        System.out.println("amount desc, id asc, null last: " + Arrays.toString(nullable));
        Arrays.sort(nullable, Comparator.nullsLast(byAmount.thenComparing(Payment::id)).reversed());
        System.out.println("whole comparator reversed: " + Arrays.toString(nullable));

        System.out.println("[adaptive comparisons, n=128]");
        Integer[] ascending = new Integer[128], descendingValues = new Integer[128];
        for (int i = 0; i < 128; i++) { ascending[i] = i; descendingValues[i] = 127 - i; }
        long[] count = {0};
        Comparator<Integer> counted = (x, y) -> { count[0]++; return Integer.compare(x, y); };
        Arrays.sort(ascending, counted);
        System.out.println("ascending: " + count[0]);
        count[0] = 0; Arrays.sort(descendingValues, counted);
        System.out.println("descending: " + count[0]);

        System.out.println("[floating point order]");
        double[] special = {Double.NaN, 0.0, -0.0, Double.NEGATIVE_INFINITY, 2.0};
        Arrays.sort(special);
        System.out.println(Arrays.toString(special));
    }
}
