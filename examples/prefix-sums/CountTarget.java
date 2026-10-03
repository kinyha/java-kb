import java.util.*;

public class CountTarget {
    static long solve(int[] values, long target) {
        Map<Long, Long> frequency = new HashMap<>();
        frequency.put(0L, 1L); // Позволяет выбрать начало перед первым элементом.
        long sum = 0, answer = 0;
        for (int value : values) {
            sum += value;
            answer += frequency.getOrDefault(sum - target, 0L); // Сначала считаем только непустые отрезки.
            frequency.merge(sum, 1L, Long::sum);
        }
        return answer;
    }

    public static void main(String[] args) {
        test(new int[]{1, 1, 1}, 2, 2);
        test(new int[]{1, 2, 3}, 3, 2);
        test(new int[]{2, -2, 2, -2}, 0, 4);
        test(new int[]{0, 0, 0}, 0, 6);
        test(new int[]{3}, 3, 1);
        test(new int[]{4, -1, 1}, 3, 1);
        test(new int[0], 0, 0);
        test(new int[]{-1, -1, 1}, -1, 3);
        test(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 4294967294L, 1);
        test(new int[100_000], 0, 5000050000L);
    }

    static void test(int[] input, long target, long want) {
        int[] before = input.clone();
        long got = solve(input, target);
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
        System.out.println("OK " + got);
    }
}
