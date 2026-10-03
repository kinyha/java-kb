import java.util.Arrays;

public class ColorsDemo {
    static void group(int[] a) {
        int lo = 0, i = 0, hi = a.length - 1;
        while (i <= hi) {
            if (a[i] == 0) swap(a, lo++, i++);
            else if (a[i] == 2) swap(a, i, hi--);
            else i++;
        }
    }
    static void swap(int[] a, int x, int y) {
        int t = a[x]; a[x] = a[y]; a[y] = t;
    }
    static void check(int[] input, int[] want) {
        group(input);
        if (!Arrays.equals(input, want)) throw new AssertionError(Arrays.toString(input));
        System.out.println("OK " + Arrays.toString(input));
    }
    public static void main(String[] args) {
        check(new int[]{2, 0, 2, 1, 1, 0}, new int[]{0, 0, 1, 1, 2, 2});
        check(new int[]{1, 2, 0}, new int[]{0, 1, 2});
        check(new int[]{2, 0}, new int[]{0, 2});
        check(new int[]{1, 1, 1}, new int[]{1, 1, 1});
        check(new int[]{2}, new int[]{2});
        check(new int[]{}, new int[]{});
    }
}
