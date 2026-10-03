import java.util.Arrays;

public class RemoveDuplicates {
    static int solve(int[] nums) {
        int write = 0;
        for (int read = 0; read < nums.length; read++) {
            if (write == 0 || nums[read] != nums[write - 1]) nums[write++] = nums[read];
        }
        return write;
    }

    public static void main(String[] args) {
        test(new int[0], new int[0]);
        test(new int[]{5}, new int[]{5});
        test(new int[]{5, 5, 5, 5}, new int[]{5});
        test(new int[]{10, 10, 20, 20, 20, 30}, new int[]{10, 20, 30});
        test(new int[]{1, 2, 3}, new int[]{1, 2, 3});
        test(new int[]{-3, -3, 0, 0, 2}, new int[]{-3, 0, 2});
        test(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE}, new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
    }

    static void test(int[] input, int[] want) {
        int capacity = input.length;
        int length = solve(input);
        if (input.length != capacity || length != want.length) throw new AssertionError("wrong length " + length);
        check(Arrays.copyOf(input, length), want); // Хвост массива не входит в ответ.
    }

    static void check(int[] got, int[] want) {
        if (!Arrays.equals(got, want)) throw new AssertionError("got " + Arrays.toString(got) + ", want " + Arrays.toString(want));
        System.out.println("OK " + Arrays.toString(got));
    }
}
