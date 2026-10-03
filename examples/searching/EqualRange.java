import java.util.*;

public class EqualRange {
    static int[] solve(int[] values, int target) {
        int first = boundary(values, target, false);
        if (first == values.length || values[first] != target) return new int[]{-1, -1};
        return new int[]{first, boundary(values, target, true) - 1}; // Последний индекс включён.
    }

    static int boundary(int[] values, int target, boolean upper) {
        int left = 0, right = values.length;
        while (left < right) {
            int middle = left + (right - left) / 2;
            // Только upper пропускает равные значения: ищет первый строго больший элемент.
            if (values[middle] < target || (upper && values[middle] == target)) left = middle + 1;
            else right = middle;
        }
        return left;
    }

    public static void main(String[] args) {
        test(new int[]{10, 20, 30, 30, 30, 50, 80}, 30, new int[]{2, 4});
        test(new int[]{30, 30, 30}, 30, new int[]{0, 2});
        test(new int[]{1}, 1, new int[]{0, 0});
        test(new int[]{1}, 0, new int[]{-1, -1});
        test(new int[0], 1, new int[]{-1, -1});
        test(new int[]{1, 2, 4}, 3, new int[]{-1, -1});
        test(new int[]{1, 2, 4}, 9, new int[]{-1, -1});
        test(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, 0}, Integer.MIN_VALUE, new int[]{0, 1});
        test(new int[]{0, Integer.MAX_VALUE, Integer.MAX_VALUE}, Integer.MAX_VALUE, new int[]{1, 2});
    }

    static void test(int[] input, int target, int[] want) {
        int[] before = input.clone();
        check(solve(input, target), want);
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
    }

    static void check(int[] got, int[] want) {
        if (!Arrays.equals(got, want)) throw new AssertionError("got " + Arrays.toString(got) + ", want " + Arrays.toString(want));
        System.out.println("OK " + Arrays.toString(got));
    }
}
