import java.util.*;

public class Lcs {
    static int solve(String a, String b) {
        if (b.length() > a.length()) { // Симметричность LCS позволяет хранить более короткую строку.
            String swap = a; a = b; b = swap;
        }
        if (b.isEmpty()) return 0;
        int[] dp = new int[b.length()+1];
        for (int i = 1; i <= a.length(); i++) {
            int diagonal = 0;
            for (int j = 1; j <= b.length(); j++) {
                int oldUp = dp[j];
                if (a.charAt(i-1) == b.charAt(j-1)) dp[j] = diagonal + 1;
                else dp[j] = Math.max(dp[j], dp[j-1]);
                diagonal = oldUp; // Для следующего j это dp[i-1][j-1], не новое dp[i][j-1].
            }
        }
        return dp[b.length()];
    }
    public static void main(String[] args) {
        check(solve("ABCD","BACD"), 3);
        check(solve("A","AA"), 1);
        check(solve("AAAA","AA"), 2);
        check(solve("","ABC"), 0);
        check(solve("ABC","DEF"), 0);
        check(solve("ABCBDAB","BDCABA"), 4);
        check(solve("ABC","ABC"), 3);
        check(solve("AXBYC","ABC"), 3);
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got,want)) throw new AssertionError("got=" + got + " want=" + want);
        System.out.println("OK got=" + got + " want=" + want);
    }
}
