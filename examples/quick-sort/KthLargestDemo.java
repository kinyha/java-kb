import java.util.Arrays;
import java.util.SplittableRandom;

public class KthLargestDemo {
    static int select(int[] input, int k) {
        if (k < 1 || k > input.length) throw new IllegalArgumentException("k out of range");
        int[] a = input.clone();
        int target = a.length - k, lo = 0, hi = a.length;
        var random = new SplittableRandom(42);
        while (true) {
            int pivot = a[random.nextInt(lo, hi)];
            int lt = lo, i = lo, gt = hi - 1;
            while (i <= gt) {
                if (a[i] < pivot) swap(a, lt++, i++);
                else if (a[i] > pivot) swap(a, i, gt--);
                else i++;
            }
            if (target < lt) hi = lt;
            else if (target > gt) lo = gt + 1;
            else return pivot;
        }
    }
    static void swap(int[] a, int x, int y) {
        int t = a[x]; a[x] = a[y]; a[y] = t;
    }
    static void check(int[] a, int k, int want) {
        int[] before = a.clone();
        int got = select(a, k);
        if (got != want || !Arrays.equals(a, before)) throw new AssertionError("selection or mutation");
        System.out.println("OK k=" + k + " value=" + got);
    }
    static void invalid(int[] a, int k) {
        try { select(a, k); throw new AssertionError("must reject k"); }
        catch (IllegalArgumentException expected) { System.out.println("OK invalid k=" + k); }
    }
    public static void main(String[] args) {
        check(new int[]{3, 2, 1, 5, 6, 4}, 2, 5);
        check(new int[]{5, 5, 2}, 2, 5);
        check(new int[]{7, 7, 7}, 3, 7);
        check(new int[]{-3, -1, -2}, 1, -1);
        check(new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE}, 3, Integer.MIN_VALUE);
        check(new int[]{9}, 1, 9);
        invalid(new int[]{}, 1);
        invalid(new int[]{1}, 0);
    }
}
