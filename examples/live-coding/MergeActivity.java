import java.util.*;
public class MergeActivity {
    record Range(long start, long end) {}
    static List<Range> solve(Range[] input) {
        if (input.length == 0) return List.of();
        Range[] sorted = input.clone();       // переставляем копию; record неизменяем
        Arrays.sort(sorted, Comparator.comparingLong(Range::start)
                .thenComparingLong(Range::end)); // сравнение без вычитания long
        List<Range> result = new ArrayList<>();
        long start = sorted[0].start(), end = sorted[0].end();
        for (int i = 1; i < sorted.length; i++) {
            Range next = sorted[i];
            if (next.start() <= end) {         // касание тоже образует один интервал объединения
                end = Math.max(end, next.end());
            } else {
                result.add(new Range(start, end));
                start = next.start();
                end = next.end();
            }
        }
        result.add(new Range(start, end));     // последний накопленный интервал ещё не выдан
        return List.copyOf(result);
    }
    static void check(String name, Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError(name + ": " + got + " != " + want);
        System.out.println(name + " OK");
    }
    public static void main(String[] args) {
        check("empty", solve(new Range[0]), List.of());
        check("one", solve(new Range[]{new Range(2, 4)}), List.of(new Range(2, 4)));
        check("overlap", solve(new Range[]{new Range(3, 8), new Range(1, 5)}), List.of(new Range(1, 8)));
        check("touch", solve(new Range[]{new Range(1, 3), new Range(3, 5)}), List.of(new Range(1, 5)));
        check("nested", solve(new Range[]{new Range(1, 10), new Range(2, 3)}), List.of(new Range(1, 10)));
        check("gap", solve(new Range[]{new Range(5, 7), new Range(1, 2)}), List.of(new Range(1, 2), new Range(5, 7)));
        check("duplicate", solve(new Range[]{new Range(1, 3), new Range(1, 3)}), List.of(new Range(1, 3)));
        check("large endpoints", solve(new Range[]{new Range(Long.MAX_VALUE - 2, Long.MAX_VALUE), new Range(0, 1)}),
                List.of(new Range(0, 1), new Range(Long.MAX_VALUE - 2, Long.MAX_VALUE)));
        Range[] values = {new Range(5, 7), new Range(1, 3)};
        Range[] before = values.clone();
        solve(values);
        check("input unchanged", Arrays.equals(values, before), true);
    }
}
