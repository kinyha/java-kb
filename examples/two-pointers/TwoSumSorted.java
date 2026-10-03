import java.util.Arrays;

public class TwoSumSorted {
    static int[] solve(int[] nums, int target) {
        int left = 0, right = nums.length - 1;
        while (left < right) {
            long sum = (long) nums[left] + nums[right];
            if (sum == target) return new int[]{left, right};
            if (sum < target) left++;
            else right--;
        }
        return new int[0];
    }

    public static void main(String[] args) {
        check(solve(new int[]{10, 20, 30, 40, 70, 90}, 60), new int[]{1, 3});
        check(solve(new int[]{5, 5}, 10), new int[]{0, 1});
        check(solve(new int[]{5}, 10), new int[0]);
        check(solve(new int[0], 0), new int[0]);
        check(solve(new int[]{-7, -3, 2, 8}, -1), new int[]{1, 2});
        check(solve(new int[]{1, 3, 8}, 20), new int[0]);
        check(solve(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}, -1), new int[]{0, 1});
        check(solve(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, -2), new int[0]);
    }

    static void check(int[] got, int[] want) {
        if (!Arrays.equals(got, want)) throw new AssertionError("got " + Arrays.toString(got) + ", want " + Arrays.toString(want));
        System.out.println("OK " + Arrays.toString(got));
    }
}
