import java.util.*;

public class GroupAnagrams {
    static List<List<String>> groupAnagrams(String[] words) {
        var groups = new LinkedHashMap<String, List<String>>();
        for (String word : words) {
            int[] count = new int[26];
            for (int i = 0; i < word.length(); i++) count[word.charAt(i) - 'a']++;
            String key = Arrays.toString(count); // Не массив и не один его hashCode.
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(word);
        }
        return new ArrayList<>(groups.values());
    }

    public static void main(String[] args) {
        check(groupAnagrams(new String[]{"pay", "bat", "yap", "aab", "tab", "aba", "abb"}),
                List.of(List.of("pay", "yap"), List.of("bat", "tab"), List.of("aab", "aba"), List.of("abb")));
        check(groupAnagrams(new String[0]), List.of());
        check(groupAnagrams(new String[]{"", ""}), List.of(List.of("", "")));
        check(groupAnagrams(new String[]{"pay", "pay"}), List.of(List.of("pay", "pay")));
        String a = "a" + "b".repeat(11), b = "a".repeat(11) + "b";
        check(groupAnagrams(new String[]{a, b}), List.of(List.of(a), List.of(b)));
        check(groupAnagrams(new String[]{"aab", "abb", "aba"}), List.of(List.of("aab", "aba"), List.of("abb")));
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
