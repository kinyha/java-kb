import java.util.*;
public class OracleDemo {
    static int baseline(String[] ids) {
        for (int j = 0; j < ids.length; j++) { // сначала минимизируем индекс повтора
            for (int i = 0; i < j; i++) {
                if (ids[i].equals(ids[j])) return j;
            }
        }
        return -1;
    }
    static int firstRepeat(String[] ids) {
        Set<String> seen = new HashSet<>();
        for (int j = 0; j < ids.length; j++) {
            if (!seen.add(ids[j])) return j; // add=false: такой ID был в префиксе
        }
        return -1;
    }
    static int wrongOrder(String[] ids) {
        for (int i = 0; i < ids.length; i++) { // этот порядок минимизирует первый индекс
            for (int j = i + 1; j < ids.length; j++) {
                if (ids[i].equals(ids[j])) return j;
            }
        }
        return -1;
    }
    static String[] input(int length, int code) {
        String[] ids = new String[length];
        String[] alphabet = {"A", "B", "C"};
        for (int i = length - 1; i >= 0; i--) {
            ids[i] = alphabet[code % 3];
            code /= 3;
        }
        return ids;
    }
    static void compareAll() {
        int checked = 0;
        String[] counterexample = null;
        for (int length = 0, count = 1; length <= 6; length++, count *= 3) {
            for (int code = 0; code < count; code++) {
                String[] ids = input(length, code);
                int want = baseline(ids);          // независимый перебор всех ранних пар
                if (firstRepeat(ids) != want) throw new AssertionError(Arrays.toString(ids));
                if (counterexample == null && wrongOrder(ids) != want) {
                    counterexample = ids.clone(); // первый короткий случай, где версии расходятся
                }
                checked++;
            }
        }
        System.out.println("checked=" + checked);
        System.out.println("first mismatch=" + Arrays.toString(counterexample));
        System.out.println("baseline=" + baseline(counterexample));
        System.out.println("wrong order=" + wrongOrder(counterexample));
    }
    public static void main(String[] args) {
        System.out.println("== small exhaustive oracle ==");
        compareAll();
    }
}
