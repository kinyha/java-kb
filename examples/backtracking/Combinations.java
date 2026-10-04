import java.util.*;

public class Combinations {
    static List<List<Integer>> solve(int n, int k) {
        List<List<Integer>> answers = new ArrayList<>();
        if (k < 0 || k > n) return answers;
        walk(n, k, 1, new ArrayList<>(), answers);
        return answers;
    }
    static void walk(int n, int k, int start, List<Integer> path, List<List<Integer>> answers) {
        int need = k - path.size();
        if (need == 0) {
            answers.add(List.copyOf(path));
            return;
        }
        for (int i = start; i <= n - need + 1; i++) { // Хватит выбранного i и всех чисел справа.
            path.add(i);
            walk(n, k, i + 1, path, answers);
            path.remove(path.size() - 1);
        }
    }

    public static void main(String[] args) {
        test(4,2, "[1, 2]","[1, 3]","[1, 4]","[2, 3]","[2, 4]","[3, 4]");
        test(4,0, "[]");
        test(0,0, "[]");
        test(3,3, "[1, 2, 3]");
        test(3,1, "[1]","[2]","[3]");
        test(2,3);
        test(4,-1);
        test(0,1);
    }
    static void test(int n, int k, String... expected) {
        List<List<Integer>> answers = solve(n,k);
        TreeSet<String> actual = new TreeSet<>();
        for (List<Integer> answer : answers) {
            if (answer.size() != k) throw new AssertionError("wrong length");
            int previous = 0;
            for (int v : answer) {
                if (v <= previous || v > n) throw new AssertionError("not an increasing choice");
                previous = v;
            }
            actual.add(answer.toString());
        }
        if (actual.size() != answers.size()) throw new AssertionError("duplicate answers");
        if (!actual.equals(new TreeSet<>(Arrays.asList(expected))))
            throw new AssertionError("got=" + actual + " want=" + Arrays.toString(expected));
        System.out.println("OK answers=" + actual);
    }
}
