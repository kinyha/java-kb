import java.util.*;

public class DuplicateIds {
    static List<String> duplicates(String[] ids) {
        var seen = new HashSet<String>();
        var repeated = new LinkedHashSet<String>();
        for (String id : ids) if (!seen.add(id)) repeated.add(id);
        return new ArrayList<>(repeated); // Второе появление определяет порядок выдачи.
    }

    public static void main(String[] args) {
        check(duplicates(new String[]{"A", "B", "A", "C", "B", "A"}), List.of("A", "B"));
        check(duplicates(new String[]{"B", "A", "B", "A"}), List.of("B", "A"));
        check(duplicates(new String[]{"A", "A", "A"}), List.of("A"));
        check(duplicates(new String[0]), List.of());
        check(duplicates(new String[]{"A"}), List.of());
        check(duplicates(new String[]{"A", "B", "C"}), List.of());
        check(duplicates(new String[]{"A", "B", "B", "A"}), List.of("B", "A"));
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
