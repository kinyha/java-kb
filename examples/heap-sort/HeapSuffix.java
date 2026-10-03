import java.util.Arrays;

public class HeapSuffix {
    // BEGIN solution
    static void keepLargest(int[] a, int k) {
        if (k < 0 || k > a.length) throw new IllegalArgumentException("k");
        int size = a.length;
        for (int root = size / 2 - 1; root >= 0; root--) siftDown(a, root, size);
        for (int taken = 0; taken < k; taken++) {
            swap(a, 0, --size);
            siftDown(a, 0, size); // остаток снова куча, суффикс уже готов
        }
    }
    // END solution
    static void siftDown(int[] a, int root, int size) {
        while (root < size / 2) {
            int child = 2 * root + 1;
            if (child + 1 < size && a[child] < a[child + 1]) child++;
            if (a[root] >= a[child]) return;
            swap(a, root, child);
            root = child;
        }
    }
    static void swap(int[] a, int x, int y) {
        int t = a[x]; a[x] = a[y]; a[y] = t;
    }
    static void check(int[] input, int k, int[] wantSuffix) {
        int[] sorted = input.clone();
        Arrays.sort(sorted); // эталон только для проверки сохранения всех значений
        keepLargest(input, k);
        int size = input.length - k;
        int[] gotSuffix = Arrays.copyOfRange(input, size, input.length);
        if (!Arrays.equals(gotSuffix, wantSuffix)) throw new AssertionError("suffix");
        for (int child = 1; child < size; child++) {
            if (input[(child - 1) / 2] < input[child]) throw new AssertionError("heap");
        }
        if (size > 0 && k > 0 && input[0] > input[size]) throw new AssertionError("boundary");
        int[] after = input.clone();
        Arrays.sort(after);
        if (!Arrays.equals(after, sorted)) throw new AssertionError("lost values");
        System.out.println("OK " + Arrays.toString(input) + " suffix=" + Arrays.toString(gotSuffix));
    }
    public static void main(String[] args) {
        check(new int[]{400, 200, 500, 100, 300}, 2, new int[]{400, 500});
        check(new int[]{500, 100, 500, 200}, 2, new int[]{500, 500});
        check(new int[]{100, 300, 200}, 0, new int[]{}); // всё равно строим кучу
        check(new int[]{300, 100, 200}, 3, new int[]{100, 200, 300});
        check(new int[]{}, 0, new int[]{});
        try {
            keepLargest(new int[]{100}, 2);
            throw new AssertionError("invalid k accepted");
        } catch (IllegalArgumentException expected) {
            System.out.println("OK invalid k");
        }
    }
}
