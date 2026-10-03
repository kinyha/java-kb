import java.util.*;

public class TwoSum {
    static int[] twoSum(int[] nums, int target) {
        var seen = new HashMap<Long, Integer>();
        for (int j = 0; j < nums.length; j++) {
            Integer i = seen.get((long) target - nums[j]); // Только предыдущие позиции.
            if (i != null) return new int[]{i, j};
            seen.putIfAbsent((long) nums[j], j); // При равных числах сохраняем ранний индекс.
        }
        return new int[0];
    }

    public static void main(String[] args) {
        check(twoSum(new int[]{2, 7, 11, 15}, 9), new int[]{0, 1});
        check(twoSum(new int[]{3, 2, 4}, 6), new int[]{1, 2});
        check(twoSum(new int[]{5, 5}, 10), new int[]{0, 1});
        check(twoSum(new int[]{5}, 10), new int[0]);
        check(twoSum(new int[0], 0), new int[0]);
        check(twoSum(new int[]{4, 4, 6}, 10), new int[]{0, 2});
        check(twoSum(new int[]{-3, 7, 2}, -1), new int[]{0, 2});
        check(twoSum(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, -2), new int[0]);
    }

    static void check(int[] got, int[] want) {
        if (!Arrays.equals(got, want)) throw new AssertionError("got " + Arrays.toString(got) + ", want " + Arrays.toString(want));
        System.out.println("OK " + Arrays.toString(got));
    }
}
