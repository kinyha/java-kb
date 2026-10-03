import java.util.*;

public class DailyTemperatures {
    static int[] solve(int[] temperatures) {
        int[] days = new int[temperatures.length];
        var pending = new ArrayDeque<Integer>();
        for (int i = 0; i < temperatures.length; i++) {
            while (!pending.isEmpty() && temperatures[pending.peek()] < temperatures[i]) {
                int earlier = pending.pop();
                days[earlier] = i - earlier; // Храним расстояние до первого строго более тёплого дня.
            }
            pending.push(i);
        }
        return days; // Для оставшихся ожиданий нули уже записаны при создании массива.
    }

    public static void main(String[] args) {
        test(new int[]{73, 74, 75, 71, 69, 72, 76, 73}, new int[]{1, 1, 4, 2, 1, 1, 0, 0});
        test(new int[]{70, 70, 65, 80}, new int[]{3, 2, 1, 0});
        test(new int[]{70, 70}, new int[]{0, 0});
        test(new int[]{4, 3, 2, 1}, new int[]{0, 0, 0, 0});
        test(new int[]{1, 2, 3, 4}, new int[]{1, 1, 1, 0});
        test(new int[]{-2, -1, -3}, new int[]{1, 0, 0});
        test(new int[0], new int[0]);
        test(new int[]{7}, new int[]{0});
        test(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}, new int[]{1, 0});
    }

    static void test(int[] input, int[] want) {
        int[] before = input.clone();
        check(solve(input), want);
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
    }

    static void check(int[] got, int[] want) {
        if (!Arrays.equals(got, want)) throw new AssertionError("got " + Arrays.toString(got) + ", want " + Arrays.toString(want));
        System.out.println("OK " + Arrays.toString(got));
    }
}
