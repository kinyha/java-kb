import java.util.*;

public class KnapsackDemo {
    record Result(int value, List<Integer> items, int[][] table) {}
    static int[] compressed(int[] weights, int[] values, int capacity, boolean ascending) {
        int[] dp = new int[capacity+1]; // Максимум при весе НЕ БОЛЬШЕ c, пустой набор разрешён.
        for (int i = 0; i < weights.length; i++) {
            if (ascending) {
                for (int c = weights[i]; c <= capacity; c++)
                    dp[c] = Math.max(dp[c], Math.addExact(dp[c-weights[i]], values[i]));
            } else {
                for (int c = capacity; c >= weights[i]; c--)
                    dp[c] = Math.max(dp[c], Math.addExact(dp[c-weights[i]], values[i]));
            }
        }
        return dp;
    }
    static Result full(int[] weights, int[] values, int capacity) {
        int[][] dp = new int[weights.length+1][capacity+1];
        for (int i = 1; i <= weights.length; i++) {
            for (int c = 0; c <= capacity; c++) {
                dp[i][c] = dp[i-1][c]; // Не брать предмет i-1.
                if (weights[i-1] <= c) {
                    int take = Math.addExact(dp[i-1][c-weights[i-1]], values[i-1]);
                    dp[i][c] = Math.max(dp[i][c], take); // Брать из ПРЕДЫДУЩЕЙ строки.
                }
            }
        }
        List<Integer> items = new ArrayList<>();
        int c = capacity;
        for (int i = weights.length; i > 0; i--) {
            if (dp[i][c] == dp[i-1][c]) continue; // При равенстве предпочитаем не брать.
            items.add(i-1);
            c -= weights[i-1];
        }
        Collections.reverse(items);
        return new Result(dp[weights.length][capacity], items, dp);
    }
    public static void main(String[] args) {
        System.out.println("== direction decides whether an item repeats ==");
        System.out.println("descending=" + Arrays.toString(compressed(new int[]{2}, new int[]{3}, 4, false)));
        System.out.println("ascending=" + Arrays.toString(compressed(new int[]{2}, new int[]{3}, 4, true)));

        System.out.println("== old rows preserve the chosen items ==");
        int[] weights = {2,3,4}, values = {4,5,10};
        Result result = full(weights, values, 6);
        System.out.println("table=" + Arrays.deepToString(result.table()));
        System.out.println("value=" + result.value() + " items=" + result.items());
        System.out.println("compressed=" + Arrays.toString(compressed(weights, values, 6, false)));

        System.out.println("== capacity is an upper bound ==");
        System.out.println("unused space=" + full(new int[]{3}, new int[]{5}, 4).value());
        System.out.println("zero capacity=" + full(new int[]{3}, new int[]{5}, 0).value());
        System.out.println("empty=" + full(new int[0], new int[0], 4).value());
    }
}
