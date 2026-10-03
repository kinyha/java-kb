import java.util.*;
import java.text.Normalizer;

public class FrequencyDemo {
    static Map<String, Long> counts(String[] ids) {
        var count = new LinkedHashMap<String, Long>();
        for (String id : ids) count.merge(id, 1L, Long::sum); // Новому ключу 1, прежнему +1.
        return count;
    }

    static List<String> duplicates(String[] ids) {
        var seen = new HashSet<String>();
        var repeated = new LinkedHashSet<String>();
        for (String id : ids) if (!seen.add(id)) repeated.add(id);
        return new ArrayList<>(repeated); // Порядок второго появления, каждый id один раз.
    }

    static String firstUnique(String[] ids) {
        Map<String, Long> count = counts(ids);
        for (String id : ids) if (count.get(id) == 1) return id; // Повторный проход сохраняет порядок входа.
        return null;
    }

    static int[] latinCounts(String s) {
        int[] count = new int[26]; // Вход: только a…z. Индекс буквы — прямой адрес.
        for (int i = 0; i < s.length(); i++) count[s.charAt(i) - 'a']++;
        return count;
    }

    static Set<Character> letters(String s) {
        var letters = new HashSet<Character>();
        for (int i = 0; i < s.length(); i++) letters.add(s.charAt(i));
        return letters;
    }

    static Map<Integer, Integer> charBag(String s) {
        var count = new TreeMap<Integer, Integer>(); // TreeMap упорядочивает только печать ключей.
        for (int i = 0; i < s.length(); i++) count.merge((int) s.charAt(i), 1, Integer::sum);
        return count;
    }

    static Map<Integer, Integer> codePointBag(String s) {
        var count = new TreeMap<Integer, Integer>();
        s.codePoints().forEach(cp -> count.merge(cp, 1, Integer::sum));
        return count;
    }

    public static void main(String[] args) {
        String[] ids = {"A", "B", "A", "C", "B", "A"};
        System.out.println("== set and counts ==");
        System.out.println("counts=" + counts(ids));
        System.out.println("duplicates=" + duplicates(ids));
        System.out.println("first unique=" + firstUnique(ids));
        System.out.println("== multiset ==");
        System.out.println("same letters=" + letters("aab").equals(letters("abb")));
        System.out.println("same counts=" + Arrays.equals(latinCounts("aab"), latinCounts("abb")));
        System.out.println("tea/eat=" + Arrays.equals(latinCounts("tea"), latinCounts("eat")));
        System.out.println("== Unicode units ==");
        String left = "\uD83D\uDE00\uD83E\uDD23";
        String right = "\uD83D\uDD23\uD83E\uDE00"; // Те же char, но пары составлены иначе.
        System.out.println("length=" + left.length() + " code points=" + left.codePointCount(0, left.length()));
        System.out.println("same char bag=" + charBag(left).equals(charBag(right)));
        System.out.println("same code point bag=" + codePointBag(left).equals(codePointBag(right)));
        String composed = "\u00E9", decomposed = "e\u0301";
        System.out.println("raw accent=" + codePointBag(composed).equals(codePointBag(decomposed)));
        String a = Normalizer.normalize(composed, Normalizer.Form.NFC);
        String b = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        System.out.println("NFC accent=" + codePointBag(a).equals(codePointBag(b)));
    }
}
