import java.util.*;

public class KthLargest {
    static int solve(int[] values, int k) {
        PriorityQueue<Integer> kept = new PriorityQueue<>();
        for (int value : values) {
            if (kept.size() < k) kept.add(value);
            else if (value > kept.peek()) { kept.remove(); kept.add(value); }
        }
        return kept.peek(); // Вход обещает 1 <= k <= n.
    }

    public static void main(String[] args) {
        check(new int[]{4, 1, 9, 2, 8, 3}, 3, 4);
        check(new int[]{9, 9, 8, 7}, 3, 8);
        check(new int[]{7}, 1, 7);
        check(new int[]{3, 2, 1}, 1, 3);
        check(new int[]{3, 2, 1}, 3, 1);
        check(new int[]{-9, -2, -5}, 2, -5);
        check(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE, 0}, 2, 0);
        check(new int[]{7, 7, 7, 7}, 4, 7);
    }
    static void check(int[] values, int k, int want) {
        int[] before = values.clone(); int got = solve(values, k);
        if (got != want || !Arrays.equals(values, before)) throw new AssertionError("wrong kth or changed input");
        System.out.println("OK k=" + k + " kth=" + got + " input=" + Arrays.toString(values));
    }
}
