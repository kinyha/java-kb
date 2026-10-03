import java.util.*;

public class ValidAnagram {
    static boolean isAnagram(String s, String t) {
        if (s.length() != t.length()) return false;
        int[] count = new int[26];
        for (int i = 0; i < s.length(); i++) count[s.charAt(i) - 'a']++;
        for (int i = 0; i < t.length(); i++) {
            if (--count[t.charAt(i) - 'a'] < 0) return false; // t запросила лишнюю копию буквы.
        }
        return true; // Длины равны: израсходован весь запас s, остатка нет.
    }

    public static void main(String[] args) {
        check(isAnagram("listen", "silent"), true);
        check(isAnagram("aab", "abb"), false);
        check(isAnagram("", ""), true);
        check(isAnagram("ab", "a"), false);
        check(isAnagram("aabb", "baba"), true);
        check(isAnagram("zx", "yx"), false);
        check(isAnagram("aaa", "aaaf"), false);
    }

    static void check(boolean got, boolean want) {
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
