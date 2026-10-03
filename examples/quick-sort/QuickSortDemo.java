import java.util.Arrays;
import java.util.SplittableRandom;

public class QuickSortDemo {
    record Band(int first, int afterLast) {}
    static long visits;
    static int maxDepth;

    static int partition2(int[] a, int lo, int hi) {
        int pivot = a[hi - 1], place = lo;
        for (int j = lo; j < hi - 1; j++) {
            visits++;
            if (a[j] < pivot) swap(a, place++, j);
        }
        swap(a, place, hi - 1);
        return place;
    }

    static Band partition3(int[] a, int lo, int hi, int pivot, boolean trace) {
        int lt = lo, i = lo, gt = hi - 1;
        if (trace) show(a, lt, i, gt);
        while (i <= gt) {
            visits++;
            if (a[i] < pivot) swap(a, lt++, i++);
            else if (a[i] > pivot) swap(a, i, gt--);
            else i++;
            if (trace) show(a, lt, i, gt);
        }
        return new Band(lt, gt + 1);
    }

    static void sort(int[] a) {
        sort(a, 0, a.length, new SplittableRandom(42), 1);
    }

    static void sort(int[] a, int lo, int hi, SplittableRandom random, int depth) {
        maxDepth = Math.max(maxDepth, depth);
        while (hi - lo > 1) {
            int pivot = a[random.nextInt(lo, hi)];
            Band equal = partition3(a, lo, hi, pivot, false);
            // Recurse only on the smaller side; handle the larger side by the loop.
            if (equal.first - lo < hi - equal.afterLast) {
                sort(a, lo, equal.first, random, depth + 1);
                lo = equal.afterLast;
            } else {
                sort(a, equal.afterLast, hi, random, depth + 1);
                hi = equal.first;
            }
        }
    }

    static int kthLargest(int[] input, int k) {
        if (k < 1 || k > input.length) throw new IllegalArgumentException("k out of range");
        int[] a = input.clone(); // Preserve the caller's journal order.
        int target = a.length - k, lo = 0, hi = a.length;
        var random = new SplittableRandom(42);
        while (true) {
            int pivot = a[random.nextInt(lo, hi)];
            Band equal = partition3(a, lo, hi, pivot, false);
            if (target < equal.first) hi = equal.first;
            else if (target >= equal.afterLast) lo = equal.afterLast;
            else return pivot;
        }
    }

    record Payment(String id, int amount) {
        @Override public String toString() { return id + ":" + amount; }
    }

    static void sortPayments(Payment[] a, int lo, int hi) {
        if (hi - lo < 2) return;
        int pivot = a[lo].amount, lt = lo, i = lo, gt = hi - 1;
        while (i <= gt) {
            if (a[i].amount < pivot) swap(a, lt++, i++);
            else if (a[i].amount > pivot) swap(a, i, gt--);
            else i++;
        }
        sortPayments(a, lo, lt);
        sortPayments(a, gt + 1, hi);
    }

    static void swap(int[] a, int x, int y) {
        int t = a[x]; a[x] = a[y]; a[y] = t;
    }
    static void swap(Payment[] a, int x, int y) {
        Payment t = a[x]; a[x] = a[y]; a[y] = t;
    }
    static void show(int[] a, int lt, int i, int gt) {
        System.out.println(Arrays.toString(a) + " lt=" + lt + " i=" + i + " gt=" + gt);
    }

    // These two fixtures always pick an extreme pivot; count the whole chain without a deep stack.
    static long extremeWork(int[] a) {
        visits = 0;
        int lo = 0, hi = a.length;
        while (hi - lo > 1) {
            int q = partition2(a, lo, hi);
            if (q == lo) lo = q + 1;
            else if (q == hi - 1) hi = q;
            else throw new AssertionError("fixture must pick an extreme");
        }
        return visits;
    }

    public static void main(String[] args) {
        System.out.println("[partition is not sorting]");
        int[] a = {400, 200, 500, 100, 300};
        int q = partition2(a, 0, a.length);
        System.out.println(Arrays.toString(a) + " pivot-index=" + q);

        System.out.println("[three-way trace, pivot=4]");
        partition3(new int[]{4, 2, 5, 1, 4}, 0, 5, 4, true);

        System.out.println("[loop iterations, n=1000]");
        int[] increasing = new int[1000], equal = new int[1000];
        for (int j = 0; j < increasing.length; j++) increasing[j] = j;
        Arrays.fill(equal, 7);
        System.out.println("last-pivot / increasing = " + extremeWork(increasing));
        System.out.println("last-pivot / equal = " + extremeWork(equal));
        visits = 0;
        Band b = partition3(equal, 0, equal.length, 7, false);
        System.out.println("three-way / equal = " + visits + " band=" + b.first + ".." + b.afterLast);

        System.out.println("[stack and output]");
        int[] input = {4, 2, 5, 1, 4};
        maxDepth = 0; sort(input);
        System.out.println(Arrays.toString(input));
        int[] manyEqual = new int[100_000]; Arrays.fill(manyEqual, 7);
        maxDepth = 0; sort(manyEqual);
        System.out.println("equal n=100000 maxDepth=" + maxDepth);

        System.out.println("[stability]");
        Payment[] payments = {new Payment("A", 4), new Payment("B", 2),
                new Payment("C", 4), new Payment("D", 1)};
        sortPayments(payments, 0, payments.length);
        System.out.println(Arrays.toString(payments));

        System.out.println("[selection]");
        int[] journal = {3, 2, 1, 5, 6, 4};
        System.out.println("k=2: " + kthLargest(journal, 2));
        System.out.println("journal: " + Arrays.toString(journal));
        System.out.println("repeated maximum, k=2: " + kthLargest(new int[]{5, 5, 2}, 2));
    }
}
