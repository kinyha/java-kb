import java.util.*;

public class RotatedSearch {
    static int solve(int[] values, int target) {
        int left = 0, right = values.length - 1;
        while (left <= right) {
            int middle = left + (right - left) / 2;
            if (values[middle] == target) return middle;
            if (values[left] <= values[middle]) { // Левая половина упорядочена, включая один элемент.
                if (values[left] <= target && target < values[middle]) right = middle - 1;
                else left = middle + 1;
            } else { // Правая половина упорядочена; ищем цель в её диапазоне.
                if (values[middle] < target && target <= values[right]) left = middle + 1;
                else right = middle - 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        test(new int[]{40, 50, 60, 70, 10, 20, 30}, 10, 4);
        test(new int[]{40, 50, 60, 70, 10, 20, 30}, 50, 1);
        test(new int[]{40, 50, 60, 70, 10, 20, 30}, 99, -1);
        test(new int[]{1, 2, 3, 4}, 4, 3);
        test(new int[]{3, 1}, 1, 1);
        test(new int[]{3, 1}, 3, 0);
        test(new int[]{3}, 3, 0);
        test(new int[]{3}, 1, -1);
        test(new int[0], 1, -1);
        test(new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE, 0}, Integer.MIN_VALUE, 1);
    }

    static void test(int[] input, int target, int want) {
        int[] before = input.clone();
        check(solve(input, target), want);
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
    }

    static void check(int got, int want) {
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
