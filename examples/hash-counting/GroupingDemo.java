import java.util.*;

public class GroupingDemo {
    static String sortedKey(String word) {
        char[] chars = word.toCharArray();
        Arrays.sort(chars);
        return new String(chars); // Строка неизменяема, равенство по содержимому.
    }

    static String countKey(String word) {
        int[] count = new int[26];
        for (int i = 0; i < word.length(); i++) count[word.charAt(i) - 'a']++;
        return Arrays.toString(count); // Разделители сохраняют границы чисел.
    }

    static List<List<String>> group(String[] words, boolean sorting) {
        var groups = new LinkedHashMap<String, List<String>>();
        for (String word : words) {
            String key = sorting ? sortedKey(word) : countKey(word);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(word);
        }
        return new ArrayList<>(groups.values()); // Группы по первому появлению, слова по входу.
    }

    record BadKey(int[] count) {} // Компонент массива сравнивается обычным equals массива.

    public static void main(String[] args) {
        String[] words = {"pay", "bat", "yap", "aab", "tab", "aba", "abb"};
        System.out.println("== canonical groups ==");
        System.out.println("sorted=" + group(words, true));
        System.out.println("counts=" + group(words, false));
        System.out.println("== key boundaries ==");
        System.out.println("joined=" + ("" + 1 + 11) + " / " + ("" + 11 + 1));
        System.out.println("delimited=" + Arrays.toString(new int[]{1, 11}) + " / " + Arrays.toString(new int[]{11, 1}));
        int[] a = {2, 1}, b = {2, 1};
        System.out.println("array content=" + Arrays.equals(a, b) + " record equals=" + new BadKey(a).equals(new BadKey(b)));
        var broken = new HashMap<BadKey, String>();
        broken.put(new BadKey(a), "aab");
        broken.put(new BadKey(b), "aba");
        var correct = new HashMap<String, String>();
        correct.put(Arrays.toString(a), "aab");
        correct.put(Arrays.toString(b), "aba");
        System.out.println("array-key size=" + broken.size() + " string-key size=" + correct.size());
        int[] x = {0, 31}, y = {1, 0};
        System.out.println("hashes=" + Arrays.hashCode(x) + "/" + Arrays.hashCode(y) + " content equal=" + Arrays.equals(x, y));
    }
}
