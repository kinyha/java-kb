import java.util.*;

public class CoinsDemo {
    record Minimum(int answer, int[] dp, List<Integer> used, int infinity) {}
    static Minimum minimum(int[] coins, int amount) {
        int infinity = Math.addExact(amount, 1); // Любая достижимая сумма требует не больше amount монет.
        int[] dp = new int[amount+1], last = new int[amount+1];
        Arrays.fill(dp, infinity);
        dp[0] = 0;
        for (int sum = 1; sum <= amount; sum++) {
            for (int coin : coins) {
                if (coin > sum || dp[sum-coin] == infinity) continue;
                int candidate = dp[sum-coin] + 1;
                if (candidate < dp[sum]) {
                    dp[sum] = candidate;
                    last[sum] = coin; // Свидетельство улучшения, а не просто последний проверенный coin.
                }
            }
        }
        List<Integer> used = new ArrayList<>();
        int answer = dp[amount] == infinity ? -1 : dp[amount];
        if (answer != -1) {
            for (int sum = amount; sum > 0; sum -= last[sum]) used.add(last[sum]);
        }
        return new Minimum(answer, dp, used, infinity);
    }
    static String show(Minimum result) {
        String[] cells = new String[result.dp().length];
        for (int i = 0; i < cells.length; i++)
            cells[i] = result.dp()[i] == result.infinity() ? "∞" : Integer.toString(result.dp()[i]);
        return Arrays.toString(cells);
    }
    static int greedy(int[] coins, int amount) {
        int[] sorted = coins.clone();
        Arrays.sort(sorted);
        int count = 0;
        for (int i = sorted.length-1; i >= 0; i--) {
            count += amount / sorted[i];
            amount %= sorted[i]; // Выбор самого большого номинала без возврата.
        }
        return amount == 0 ? count : -1;
    }
    static long count(int[] coins, int amount, boolean ordered) {
        long[] dp = new long[amount+1];
        dp[0] = 1; // Пустая последовательность или пустой набор — один способ.
        if (ordered) {
            for (int sum = 1; sum <= amount; sum++) {
                for (int coin : coins) {
                    if (coin <= sum) dp[sum] = Math.addExact(dp[sum], dp[sum-coin]);
                }
            }
        } else {
            for (int coin : coins) { // После этого прохода разрешены только уже обработанные номиналы.
                for (int sum = coin; sum <= amount; sum++)
                    dp[sum] = Math.addExact(dp[sum], dp[sum-coin]);
            }
        }
        return dp[amount];
    }
    public static void main(String[] args) {
        System.out.println("== minimum is not the biggest coin first ==");
        int[] coins = {1,3,4};
        Minimum best = minimum(coins, 6);
        System.out.println("table=" + show(best));
        System.out.println("minimum=" + best.answer() + " used=" + best.used());
        System.out.println("greedy=" + greedy(coins, 6));

        System.out.println("== unreachable is not zero ==");
        Minimum impossible = minimum(new int[]{4,6}, 5);
        System.out.println("table=" + show(impossible));
        System.out.println("answer=" + impossible.answer() + " used=" + impossible.used());
        System.out.println("empty zero=" + minimum(new int[0], 0).answer());
        System.out.println("empty positive=" + minimum(new int[0], 4).answer());

        System.out.println("== loop order changes what is counted ==");
        System.out.println("combinations=" + count(new int[]{1,2}, 4, false));
        System.out.println("sequences=" + count(new int[]{1,2}, 4, true));
        System.out.println("zero combinations=" + count(new int[]{1,2}, 0, false));
    }
}
