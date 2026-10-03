import java.util.Arrays;

public class RestoreRoot {
    // BEGIN solution
    static void restoreRoot(int[] a, int size) {
        int root = 0;
        while (root < size / 2) {
            int child = 2 * root + 1;
            if (child + 1 < size && a[child] < a[child + 1]) child++; // больший из существующих
            if (a[root] >= a[child]) return; // оба ребёнка уже не больше
            int t = a[root]; a[root] = a[child]; a[child] = t;
            root = child; // единственная ветвь, где осталось нарушение
        }
    }
    // END solution
    static void check(int[] input, int size, int[] want) {
        restoreRoot(input, size);
        if (!Arrays.equals(input, want)) throw new AssertionError(Arrays.toString(input));
        System.out.println("OK " + Arrays.toString(input) + " size=" + size);
    }
    public static void main(String[] args) {
        check(new int[]{100, 900, 700, 800, 600, 200, 500}, 7,
                new int[]{900, 800, 700, 100, 600, 200, 500});
        check(new int[]{100, 200, 300}, 3, new int[]{300, 200, 100}); // справа больше
        check(new int[]{100, 200}, 2, new int[]{200, 100}); // правого ребёнка нет
        check(new int[]{100, 200, 999}, 2, new int[]{200, 100, 999}); // суффикс защищён
        check(new int[]{7, 7, 7}, 3, new int[]{7, 7, 7});
        check(new int[]{100}, 1, new int[]{100});
        check(new int[]{}, 0, new int[]{});
    }
}
