import java.util.Arrays;

public class ReverseArray {
    static void solve(int[] nums) {
        for (int left = 0, right = nums.length - 1; left < right; left++, right--) {
            int saved = nums[left];
            nums[left] = nums[right];
            nums[right] = saved;
        }
    }

    public static void main(String[] args) {
        test(new int[0], new int[0]);
        test(new int[]{7}, new int[]{7});
        test(new int[]{1, 2}, new int[]{2, 1});
        test(new int[]{10, 20, 30, 40, 50}, new int[]{50, 40, 30, 20, 10});
        test(new int[]{1, 1, 2, 2}, new int[]{2, 2, 1, 1});
        test(new int[]{-5, 0, 7}, new int[]{7, 0, -5});
    }

    static void test(int[] input, int[] want) {
        int[] before = input.clone();
        solve(input);
        check(input, want);
        solve(input); // Два разворота восстанавливают весь вход.
        check(input, before);
    }

    static void check(int[] got, int[] want) {
        if (!Arrays.equals(got, want)) throw new AssertionError("got " + Arrays.toString(got) + ", want " + Arrays.toString(want));
        System.out.println("OK " + Arrays.toString(got));
    }
}
