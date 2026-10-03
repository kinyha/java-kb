import java.util.*;

public class DivisibleRanges {
    static long solve(int[] values, int divisor) {
        Map<Integer, Long> frequency = new HashMap<>();
        frequency.put(0, 1L);
        long sum = 0, answer = 0;
        for (int value : values) {
            sum += value;
            int remainder = (int) Math.floorMod(sum, (long) divisor); // При divisor > 0 всегда в [0, divisor).
            answer += frequency.getOrDefault(remainder, 0L);
            frequency.merge(remainder, 1L, Long::sum);
        }
        return answer;
    }

    public static void main(String[] args) {
        test(new int[]{4, 5, 0, -2, -3, 1}, 5, 7);
        test(new int[]{5}, 9, 0);
        test(new int[]{-1, 5}, 5, 1);
        test(new int[]{-5}, 5, 1);
        test(new int[]{0, 0, 0}, 7, 6);
        test(new int[]{2, -2}, 1, 3);
        test(new int[0], 5, 0);
        test(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, Integer.MAX_VALUE, 3);
        test(new int[]{Integer.MIN_VALUE, 1}, Integer.MAX_VALUE, 1);
        test(new int[100_000], 13, 5000050000L);
    }

    static void test(int[] input, int divisor, long want) {
        int[] before = input.clone();
        long got = solve(input, divisor);
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
        System.out.println("OK " + got);
    }
}
