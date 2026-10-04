import java.util.*;

public class Permutations {
    static List<List<Integer>> solve(int[] values) {
        List<List<Integer>> answers = new ArrayList<>();
        walk(values, new boolean[values.length], new ArrayList<>(), answers);
        return answers;
    }
    static void walk(int[] values, boolean[] used, List<Integer> path, List<List<Integer>> answers) {
        if (path.size() == values.length) {
            answers.add(List.copyOf(path));
            return;
        }
        for (int i = 0; i < values.length; i++) {
            if (used[i]) continue;
            used[i] = true;                   // used относится к текущему пути, не ко всему поиску.
            path.add(values[i]);
            walk(values, used, path, answers);
            path.remove(path.size() - 1);
            used[i] = false;
        }
    }

    public static void main(String[] args) {
        test(new int[]{4,7,9}, "[4, 7, 9]","[4, 9, 7]","[7, 4, 9]","[7, 9, 4]","[9, 4, 7]","[9, 7, 4]");
        test(new int[0], "[]");
        test(new int[]{5}, "[5]");
        test(new int[]{0,-2}, "[0, -2]","[-2, 0]");
        test(new int[]{9,4}, "[9, 4]","[4, 9]");
        test(new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE}, "[-2147483648, 2147483647]","[2147483647, -2147483648]");
    }

    static void test(int[] input, String... expected) {
        int[] before = input.clone();
        List<List<Integer>> answers = solve(input);
        TreeSet<String> actual = new TreeSet<>();
        for (List<Integer> answer : answers) actual.add(answer.toString());
        if (actual.size() != answers.size()) throw new AssertionError("duplicate answers");
        if (!actual.equals(new TreeSet<>(Arrays.asList(expected))))
            throw new AssertionError("got=" + actual + " want=" + Arrays.toString(expected));
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
        System.out.println("OK answers=" + actual);
    }
}
