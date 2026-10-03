import java.util.*;

public class LargestRectangle {
    static long solve(int[] heights) {
        int[] pending = new int[heights.length];
        int size = 0;
        long best = 0;
        for (int right = 0; right <= heights.length; right++) {
            int current = right == heights.length ? 0 : heights[right]; // Виртуальный ноль завершает ожидания.
            while (size > 0 && heights[pending[size - 1]] >= current) {
                int bar = pending[--size];
                int left = size == 0 ? -1 : pending[size - 1];
                long area = (long) heights[bar] * (right - left - 1); // Границы не входят в прямоугольник.
                best = Math.max(best, area);
            }
            if (right < heights.length) pending[size++] = right; // Виртуальный индекс не попадает в стек.
        }
        return best;
    }

    public static void main(String[] args) {
        test(new int[]{2, 1, 5, 6, 2, 3}, 10);
        test(new int[]{2, 2, 2}, 6);
        test(new int[]{1, 2, 3, 4}, 6);
        test(new int[]{4, 3, 2, 1}, 6);
        test(new int[]{0, 2, 0, 2}, 2);
        test(new int[]{5}, 5);
        test(new int[]{0, 0}, 0);
        test(new int[0], 0);
        test(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 4294967294L);
    }

    static void test(int[] input, long want) {
        int[] before = input.clone();
        check(solve(input), want);
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
    }

    static void check(long got, long want) {
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
