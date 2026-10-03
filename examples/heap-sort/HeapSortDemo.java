import java.util.Arrays;
import java.util.Comparator;

public class HeapSortDemo {
    static long comparisons, swaps;

    // BEGIN core
    static void siftDown(int[] a, int root, int size) {
        while (root < size / 2) {           // только узлы с левым ребёнком
            int child = 2 * root + 1;
            if (child + 1 < size) {
                comparisons++;
                if (a[child] < a[child + 1]) child++; // выбираем большего ребёнка
            }
            comparisons++;
            if (a[root] >= a[child]) return; // в поддеревьях порядок уже верен
            swap(a, root, child);
            root = child;                  // нарушение могло уйти только сюда
        }
    }
    static void swap(int[] a, int x, int y) {
        int t = a[x]; a[x] = a[y]; a[y] = t;
        swaps++;
    }
    static void heapify(int[] a) {
        for (int root = a.length / 2 - 1; root >= 0; root--) {
            siftDown(a, root, a.length);    // дети обработаны раньше родителя
        }
    }
    static void sort(int[] a) {
        heapify(a);
        for (int size = a.length; size > 1;) {
            swap(a, 0, --size);             // максимум занимает начало суффикса
            siftDown(a, 0, size);           // суффикс [size, n) больше не трогаем
        }
    }
    // END core

    // BEGIN root-trace
    static void traceRoot(int[] a, int size) {
        int root = 0;
        show(a, root, "start");
        while (root < size / 2) {
            int child = 2 * root + 1;
            if (child + 1 < size && a[child] < a[child + 1]) child++;
            if (a[root] >= a[child]) break;
            swap(a, root, child);
            root = child;
            show(a, root, "swap");
        }
        show(a, root, "done");
    }
    static void show(int[] a, int root, String phase) {
        System.out.println(Arrays.toString(a) + " root=" + root + " " + phase);
    }
    // END root-trace

    // BEGIN build-trace
    static void traceBuild(int[] a) {
        System.out.println("input " + Arrays.toString(a));
        for (int root = a.length / 2 - 1; root >= 0; root--) {
            siftDown(a, root, a.length);
            System.out.println("root=" + root + " " + Arrays.toString(a));
        }
    }
    // END build-trace

    // BEGIN sort-trace
    static void traceSort(int[] a) {
        heapify(a);
        System.out.println("heap size=" + a.length + " " + Arrays.toString(a));
        for (int size = a.length; size > 1;) {
            swap(a, 0, --size);
            if (size == a.length - 1) {
                System.out.println("swap size=" + size + " " + Arrays.toString(a));
            }
            siftDown(a, 0, size);
            System.out.println("heap size=" + size + " " + Arrays.toString(a));
        }
    }
    // END sort-trace

    // BEGIN work
    static void work(String name, int[] a) {
        comparisons = 0; swaps = 0;
        heapify(a);
        long buildComparisons = comparisons, buildSwaps = swaps;
        for (int size = a.length; size > 1;) {
            swap(a, 0, --size);
            siftDown(a, 0, size);
        }
        System.out.println(name + ": build comparisons=" + buildComparisons
                + ", build swaps=" + buildSwaps + ", total comparisons=" + comparisons);
    }
    // END work

    // BEGIN payments
    record Payment(long id, long amount) {
        @Override public String toString() { return id + ":" + amount; }
    }
    static void sortPayments(Payment[] a, Comparator<Payment> order) {
        for (int root = a.length / 2 - 1; root >= 0; root--) sink(a, root, a.length, order);
        for (int size = a.length; size > 1;) {
            Payment t = a[0]; a[0] = a[--size]; a[size] = t;
            sink(a, 0, size, order);
        }
    }
    static void sink(Payment[] a, int root, int size, Comparator<Payment> order) {
        while (root < size / 2) {
            int child = 2 * root + 1;
            if (child + 1 < size && order.compare(a[child], a[child + 1]) < 0) child++;
            if (order.compare(a[root], a[child]) >= 0) return;
            Payment t = a[root]; a[root] = a[child]; a[child] = t;
            root = child;
        }
    }
    // END payments

    public static void main(String[] args) {
        System.out.println("[root]");
        traceRoot(new int[]{100, 900, 700, 800, 600, 200, 500}, 7);
        System.out.println("[build]");
        traceBuild(new int[]{400, 200, 500, 100, 300});
        System.out.println("[sort]");
        traceSort(new int[]{400, 200, 500, 100, 300});
        System.out.println("[work]");
        int[] increasing = new int[1000], equal = new int[1000];
        for (int i = 0; i < increasing.length; i++) increasing[i] = i;
        Arrays.fill(equal, 7);
        work("increasing n=1000", increasing);
        work("equal n=1000", equal);
        System.out.println("[stability]");
        Payment[] p = {new Payment(10, 100), new Payment(20, 100), new Payment(30, 200)};
        System.out.println("before " + Arrays.toString(p));
        sortPayments(p, Comparator.comparingLong(Payment::amount));
        System.out.println("after  " + Arrays.toString(p));
    }
}
