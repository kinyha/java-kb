import java.util.*;
public class BurstWindow {

    static int solve(long[] times, long width) {
        int left = 0, best = 0;
        for (int right = 0; right < times.length; right++) {
            while (times[right] - times[left] >= width) { // правая граница полуоткрытая
                left++;                           // сортировка и width>0 оставят хотя бы right
            }
            best = Math.max(best, right - left + 1);
        }
        return best;
    }
    static void check(String name, Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError(name + ": " + got + " != " + want);
        System.out.println(name + " OK");
    }
    public static void main(String[] args) {
        check("empty", solve(new long[0], 5), 0);
        check("one", solve(new long[]{7}, 5), 1);
        check("boundary", solve(new long[]{0, 5}, 5), 1);
        check("duplicates", solve(new long[]{2, 2, 2}, 1), 3);
        check("dense", solve(new long[]{0, 2, 3, 6, 7}, 5), 3);
        check("several removals", solve(new long[]{0, 1, 2, 100}, 3), 3);
        check("near max", solve(new long[]{Long.MAX_VALUE - 2, Long.MAX_VALUE - 1, Long.MAX_VALUE}, 3), 3);
        check("max width boundary", solve(new long[]{0, Long.MAX_VALUE}, Long.MAX_VALUE), 1);
        long[] values = {1, 2, 3};
        long[] before = values.clone();
        solve(values, 2);
        check("input unchanged", Arrays.equals(values, before), true);
    }
}
