import java.util.*;

public class LongestConsecutive {
    static int longestConsecutive(int[] nums) {
        var values = new HashSet<Integer>();
        for (int x : nums) values.add(x);
        int best = 0;
        for (int start : values) { // По уникальным числам: дубли старта не повторяют всю цепочку.
            if (start != Integer.MIN_VALUE && values.contains(start - 1)) continue;
            int end = start, length = 1; // Предшественника нет: это начало одной цепочки.
            while (end != Integer.MAX_VALUE && values.contains(end + 1)) {
                end++;
                length++;
            }
            best = Math.max(best, length);
        }
        return best;
    }

    public static void main(String[] args) {
        check(longestConsecutive(new int[]{100, 4, 200, 1, 3, 2}), 4);
        check(longestConsecutive(new int[]{0, 3, 7, 2, 5, 8, 4, 6, 0, 1}), 9);
        check(longestConsecutive(new int[0]), 0);
        check(longestConsecutive(new int[]{-5}), 1);
        check(longestConsecutive(new int[]{-3, -1, -2, 5}), 3);
        check(longestConsecutive(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}), 1);
        check(longestConsecutive(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1, 0}), 2);
        check(longestConsecutive(new int[]{Integer.MAX_VALUE - 1, Integer.MAX_VALUE}), 2);
        check(longestConsecutive(new int[]{1, 1, 1, 2, 3}), 3);
    }

    static void check(int got, int want) {
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
