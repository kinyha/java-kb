import java.util.*;

public class StairsDemo {
    static long plain(int n, long[] calls) {
        calls[0]++;
        if (n <= 1) return 1; // До нулевой ступени один способ: ничего не делать.
        return plain(n-1, calls) + plain(n-2, calls);
    }
    static long memo(int n, long[] values, boolean[] ready, long[] calls) {
        calls[0]++;
        if (ready[n]) return values[n];
        values[n] = Math.addExact(memo(n-1, values, ready, calls), memo(n-2, values, ready, calls));
        ready[n] = true;      // Значение становится готовым после вычисления, не при входе.
        return values[n];
    }
    static long[] table(int n) {
        long[] dp = new long[n+1];
        dp[0] = 1;
        if (n > 0) dp[1] = 1;
        for (int i = 2; i <= n; i++) dp[i] = Math.addExact(dp[i-1], dp[i-2]);
        return dp;
    }
    static long rolling(int n) {
        long previous = 1, current = 1;
        for (int i = 2; i <= n; i++) {
            long next = Math.addExact(previous, current); // Оба старых значения нужны до сдвига.
            previous = current;
            current = next;
        }
        return current;
    }
    static long wrongOrder(int n) {
        long previous = 1, current = 1;
        for (int i = 2; i <= n; i++) {
            previous = current;
            current = previous + current; // Ошибка: предыдущая ступень уже потеряна.
        }
        return current;
    }
    static int intWays(int n) {
        int previous = 1, current = 1;
        for (int i = 2; i <= n; i++) {
            int next = previous + current; // Намеренно без защиты от переполнения.
            previous = current;
            current = next;
        }
        return current;
    }
    public static void main(String[] args) {
        System.out.println("== repeated branches share one state ==");
        int n = 12;
        long[] plainCalls = {0}, memoCalls = {0};
        long[] values = new long[n+1];
        boolean[] ready = new boolean[n+1];
        values[0] = values[1] = 1;
        ready[0] = ready[1] = true;
        System.out.println("plain ways=" + plain(n, plainCalls) + " calls=" + plainCalls[0]);
        System.out.println("memo ways=" + memo(n, values, ready, memoCalls) + " calls=" + memoCalls[0]);

        System.out.println("== keep the needed old values ==");
        System.out.println("table=" + Arrays.toString(table(6)));
        System.out.println("rolling=" + rolling(6));
        System.out.println("wrong order=" + wrongOrder(6));
        System.out.println("zero=" + rolling(0));

        System.out.println("== counts can overflow ==");
        System.out.println("n46 long=" + rolling(46));
        System.out.println("n46 int=" + intWays(46));
        try {
            rolling(92);
        } catch (ArithmeticException e) {
            System.out.println("n92=" + e.getClass().getSimpleName());
        }
    }
}
