import java.util.*;

public class CombinationSum {
    static List<List<Integer>> solve(int[] candidates, int target) {
        int[] values = candidates.clone();
        Arrays.sort(values);
        List<List<Integer>> answers = new ArrayList<>();
        if (target >= 0) walk(values, target, 0, new ArrayList<>(), answers);
        return answers;
    }
    static void walk(int[] values, int remaining, int start, List<Integer> path,
                     List<List<Integer>> answers) {
        if (remaining == 0) {
            answers.add(List.copyOf(path));
            return;
        }
        for (int i = start; i < values.length; i++) {
            if (values[i] > remaining) break; // Дальше только большие значения.
            path.add(values[i]);
            walk(values, remaining - values[i], i, path, answers); // i разрешает повтор этого числа.
            path.remove(path.size() - 1);
        }
    }

    public static void main(String[] args) {
        test(new int[]{7,2,3}, 7, "[2, 2, 3]","[7]");
        test(new int[]{2,3,5}, 8, "[2, 2, 2, 2]","[2, 3, 3]","[3, 5]");
        test(new int[]{2}, 1);
        test(new int[]{3}, 9, "[3, 3, 3]");
        test(new int[]{4,6}, 0, "[]");
        test(new int[0], 0, "[]");
        test(new int[0], 5);
        test(new int[]{Integer.MAX_VALUE}, Integer.MAX_VALUE, "[2147483647]");
    }
    static void test(int[] input, int target, String... expected) {
        int[] before = input.clone();
        List<List<Integer>> answers = solve(input,target);
        TreeSet<String> actual = new TreeSet<>();
        for (List<Integer> answer : answers) {
            long sum = 0;
            int previous = 0;
            for (int v : answer) {
                if (v < previous || Arrays.stream(input).noneMatch(x -> x == v))
                    throw new AssertionError("invalid candidate order");
                previous = v;
                sum += v;
            }
            if (sum != target) throw new AssertionError("wrong sum");
            actual.add(answer.toString());
        }
        if (actual.size() != answers.size()) throw new AssertionError("duplicate answers");
        if (!actual.equals(new TreeSet<>(Arrays.asList(expected))))
            throw new AssertionError("got=" + actual + " want=" + Arrays.toString(expected));
        if (!Arrays.equals(input,before)) throw new AssertionError("input changed");
        System.out.println("OK answers=" + actual);
    }
}
