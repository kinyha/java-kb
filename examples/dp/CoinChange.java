import java.util.*;

public class CoinChange {
    static int solve(int[] coins, int amount) {
        int infinity = amount + 1;
        int[] dp = new int[amount+1];
        Arrays.fill(dp, infinity);
        dp[0] = 0;
        for (int sum = 1; sum <= amount; sum++) {
            for (int coin : coins) {
                if (coin > sum || dp[sum-coin] == infinity) continue;
                dp[sum] = Math.min(dp[sum], dp[sum-coin] + 1);
            }
        }
        return dp[amount] == infinity ? -1 : dp[amount];
    }
    public static void main(String[] args) {
        test(new int[]{1,3,4}, 6, 2);
        test(new int[]{2}, 3, -1);
        test(new int[]{4,6}, 5, -1);
        test(new int[0], 0, 0);
        test(new int[0], 5, -1);
        test(new int[]{7,2,3}, 7, 1);
        test(new int[]{2,2,3}, 7, 3);
        test(new int[]{1}, 10000, 10000);
    }
    static void test(int[] coins, int amount, int want) {
        int[] before = coins.clone();
        check(solve(coins, amount), want);
        if (!Arrays.equals(coins,before)) throw new AssertionError("input changed");
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got,want)) throw new AssertionError("got=" + got + " want=" + want);
        System.out.println("OK got=" + got + " want=" + want);
    }
}
