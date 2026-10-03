import java.util.*;

public class BalancedRange {
    static int solve(int[] values) {
        Map<Integer, Integer> first = new HashMap<>();
        first.put(0, 0);
        int balance = 0, best = 0;
        for (int right = 1; right <= values.length; right++) {
            balance += values[right - 1] == 0 ? -1 : 1; // Сумма равна количеству единиц минус количество нулей.
            Integer left = first.get(balance);
            if (left != null) best = Math.max(best, right - left);
            first.putIfAbsent(balance, right); // Первая граница даёт наибольшую длину для будущих right.
        }
        return best;
    }

    public static void main(String[] args) {
        test(new int[]{0, 1}, 2);
        test(new int[]{0, 1, 0}, 2);
        test(new int[]{0, 1, 0, 1, 1, 0}, 6);
        test(new int[]{0, 0, 1, 1, 0}, 4);
        test(new int[]{1, 1, 1}, 0);
        test(new int[]{0, 0}, 0);
        test(new int[]{1}, 0);
        test(new int[0], 0);
        test(new int[]{1, 0, 1, 0, 1, 0, 1}, 6);
    }

    static void test(int[] input, int want) {
        int[] before = input.clone();
        int got = solve(input);
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
        System.out.println("OK " + got);
    }
}
