import java.util.*;

public class Subsets {
    static List<List<Integer>> solve(int[] values) {
        List<List<Integer>> answers = new ArrayList<>();
        walk(values, 0, new ArrayList<>(), answers);
        return answers;
    }
    static void walk(int[] values, int start, List<Integer> path, List<List<Integer>> answers) {
        answers.add(List.copyOf(path));       // Каждый префикс — готовое подмножество.
        for (int i = start; i < values.length; i++) {
            path.add(values[i]);
            walk(values, i + 1, path, answers); // Возрастают индексы, а не сами значения.
            path.remove(path.size() - 1);
        }
    }

    public static void main(String[] args) {
        test(new int[]{4,7,9}, "[]","[4]","[4, 7]","[4, 7, 9]","[4, 9]","[7]","[7, 9]","[9]");
        test(new int[0], "[]");
        test(new int[]{5}, "[]","[5]");
        test(new int[]{0,-2}, "[]","[0]","[0, -2]","[-2]");
        test(new int[]{9,4}, "[]","[9]","[9, 4]","[4]");
        test(new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE}, "[]","[-2147483648]","[-2147483648, 2147483647]","[2147483647]");
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
