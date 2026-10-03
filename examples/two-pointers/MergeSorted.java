import java.util.Arrays;

public class MergeSorted {
    static void solve(int[] nums, int m, int[] other, int n) {
        int i = m - 1, j = n - 1, write = m + n - 1;
        while (j >= 0) {
            if (i >= 0 && nums[i] > other[j]) nums[write--] = nums[i--];
            else nums[write--] = other[j--];
        }
    }

    public static void main(String[] args) {
        test(new int[]{10, 30, 50, 0, 0, 0}, 3, new int[]{20, 40, 60}, new int[]{10, 20, 30, 40, 50, 60});
        test(new int[]{2, 4, 6, 0, 0, 0}, 3, new int[]{1, 3, 5}, new int[]{1, 2, 3, 4, 5, 6});
        test(new int[]{0, 0}, 0, new int[]{-3, 2}, new int[]{-3, 2});
        test(new int[]{1, 3}, 2, new int[0], new int[]{1, 3});
        test(new int[0], 0, new int[0], new int[0]);
        test(new int[]{-3, 0, 4, 0, 0}, 3, new int[]{0, 2}, new int[]{-3, 0, 0, 2, 4});
        test(new int[]{2, 2, 0, 0}, 2, new int[]{2, 2}, new int[]{2, 2, 2, 2});
        test(new int[]{8, 9, 0}, 2, new int[]{1}, new int[]{1, 8, 9});
    }

    static void test(int[] nums, int m, int[] other, int[] want) {
        int[] originalOther = other.clone();
        solve(nums, m, other, other.length);
        check(nums, want);
        if (!Arrays.equals(other, originalOther)) throw new AssertionError("other changed");
    }

    static void check(int[] got, int[] want) {
        if (!Arrays.equals(got, want)) throw new AssertionError("got " + Arrays.toString(got) + ", want " + Arrays.toString(want));
        System.out.println("OK " + Arrays.toString(got));
    }
}
