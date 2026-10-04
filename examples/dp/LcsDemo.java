import java.util.*;

public class LcsDemo {
    static int[][] table(String a, String b) {
        int[][] dp = new int[a.length()+1][b.length()+1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i-1) == b.charAt(j-1)) dp[i][j] = dp[i-1][j-1] + 1;
                else dp[i][j] = Math.max(dp[i-1][j], dp[i][j-1]);
            }
        }
        return dp;
    }
    static String restore(String a, String b, int[][] dp) {
        StringBuilder result = new StringBuilder();
        int i = a.length(), j = b.length();
        while (i > 0 && j > 0) {
            if (a.charAt(i-1) == b.charAt(j-1)) {
                result.append(a.charAt(i-1));
                i--; j--;
            } else if (dp[i-1][j] >= dp[i][j-1]) i--; // Правило выбора при равной длине.
            else j--;
        }
        return result.reverse().toString();
    }
    static int rolling(String a, String b, boolean wrongDiagonal) {
        int[] dp = new int[b.length()+1];
        for (int i = 1; i <= a.length(); i++) {
            int diagonal = 0;
            for (int j = 1; j <= b.length(); j++) {
                int oldUp = dp[j]; // Значение dp[i-1][j] до перезаписи.
                if (a.charAt(i-1) == b.charAt(j-1)) dp[j] = diagonal + 1;
                else dp[j] = Math.max(dp[j], dp[j-1]);
                diagonal = wrongDiagonal ? dp[j] : oldUp; // Ошибка хранит новую строку вместо старой.
            }
        }
        return dp[b.length()];
    }
    public static void main(String[] args) {
        System.out.println("== prefixes keep the relative order ==");
        String a = "ABCD", b = "BACD";
        int[][] dp = table(a, b);
        System.out.println("table=" + Arrays.deepToString(dp));
        System.out.println("length=" + dp[a.length()][b.length()] + " subsequence=" + restore(a, b, dp));

        System.out.println("== diagonal belongs to the old row ==");
        System.out.println("correct A/AA=" + rolling("A", "AA", false));
        System.out.println("wrong A/AA=" + rolling("A", "AA", true));
        System.out.println("rolling ABCD/BACD=" + rolling(a, b, false));

        System.out.println("== empty prefixes and repeated characters ==");
        System.out.println("empty=" + rolling("", "ABC", false));
        System.out.println("AAAA/AA=" + rolling("AAAA", "AA", false));
        System.out.println("ABC/DEF=" + rolling("ABC", "DEF", false));
    }
}
