import java.util.*;

public class Knapsack {
    static int solve(int[] weights, int[] values, int capacity) {
        int[] dp = new int[capacity+1];
        for (int i = 0; i < weights.length; i++) {
            for (int c = capacity; c >= weights[i]; c--) {
                dp[c] = Math.max(dp[c], Math.addExact(dp[c-weights[i]], values[i]));
            }
        }
        return dp[capacity];
    }
    public static void main(String[] args) {
        test(new int[]{2}, new int[]{3}, 4, 3);
        test(new int[]{2,3,4}, new int[]{4,5,10}, 6, 14);
        test(new int[]{3}, new int[]{5}, 4, 5);
        test(new int[]{3}, new int[]{5}, 0, 0);
        test(new int[0], new int[0], 4, 0);
        test(new int[]{1,1}, new int[]{2,3}, 2, 5);
        test(new int[]{5,2}, new int[]{100,4}, 3, 4);
        test(new int[]{1,2}, new int[]{0,0}, 4, 0);
    }
    static void test(int[] weights, int[] values, int capacity, int want) {
        int[] beforeWeights = weights.clone(), beforeValues = values.clone();
        check(solve(weights, values, capacity), want);
        if (!Arrays.equals(weights,beforeWeights) || !Arrays.equals(values,beforeValues))
            throw new AssertionError("input changed");
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got,want)) throw new AssertionError("got=" + got + " want=" + want);
        System.out.println("OK got=" + got + " want=" + want);
    }
}
