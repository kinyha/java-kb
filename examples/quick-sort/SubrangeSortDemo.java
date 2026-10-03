import java.util.Arrays;
import java.util.SplittableRandom;

public class SubrangeSortDemo {
    record Band(int first, int afterLast) {}

    static void sortRange(int[] a, int lo, int hi) {
        if (lo < 0 || hi > a.length || lo > hi) throw new IllegalArgumentException("bad range");
        sort(a, lo, hi, new SplittableRandom(42));
    }
    static void sort(int[] a, int lo, int hi, SplittableRandom random) {
        while (hi - lo > 1) {
            int pivot = a[random.nextInt(lo, hi)];
            Band b = partition(a, lo, hi, pivot);
            if (b.first - lo < hi - b.afterLast) {
                sort(a, lo, b.first, random);
                lo = b.afterLast;
            } else {
                sort(a, b.afterLast, hi, random);
                hi = b.first;
            }
        }
    }
    static Band partition(int[] a, int lo, int hi, int pivot) {
        int lt = lo, i = lo, gt = hi - 1;
        while (i <= gt) {
            if (a[i] < pivot) swap(a, lt++, i++);
            else if (a[i] > pivot) swap(a, i, gt--);
            else i++;
        }
        return new Band(lt, gt + 1);
    }
    static void swap(int[] a, int x, int y) {
        int t = a[x]; a[x] = a[y]; a[y] = t;
    }
    static void check(int[] a, int lo, int hi, int[] want) {
        sortRange(a, lo, hi);
        if (!Arrays.equals(a, want)) throw new AssertionError(Arrays.toString(a));
        System.out.println("OK " + Arrays.toString(a));
    }
    static void invalid(int lo, int hi) {
        try { sortRange(new int[]{1, 2}, lo, hi); throw new AssertionError("must reject range"); }
        catch (IllegalArgumentException expected) { System.out.println("OK invalid range=" + lo + ".." + hi); }
    }
    public static void main(String[] args) {
        check(new int[]{99, 4, 1, 4, 2, -99}, 1, 5, new int[]{99, 1, 2, 4, 4, -99});
        check(new int[]{3, 2, 1}, 1, 1, new int[]{3, 2, 1});
        check(new int[]{3, 2, 1}, 1, 2, new int[]{3, 2, 1});
        check(new int[]{}, 0, 0, new int[]{});
        invalid(-1, 1);
        invalid(0, 3);
        invalid(2, 1);
        var random = new SplittableRandom(7);
        for (int trial = 0; trial < 1000; trial++) {
            int[] a = new int[random.nextInt(128)];
            for (int i = 0; i < a.length; i++) a[i] = random.nextInt(-5, 6);
            int lo = random.nextInt(a.length + 1), hi = random.nextInt(lo, a.length + 1);
            int[] want = a.clone(); Arrays.sort(want, lo, hi);
            sortRange(a, lo, hi);
            if (!Arrays.equals(a, want)) throw new AssertionError("range or sorting changed");
        }
        System.out.println("OK 1000 random subranges");
        int[] equal = new int[100_000]; Arrays.fill(equal, 7);
        sortRange(equal, 0, equal.length);
        for (int x : equal) if (x != 7) throw new AssertionError("value changed");
        System.out.println("OK 100000 equal values");
    }
}
