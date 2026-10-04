import java.util.*;

public class ChoicesDemo {
    record Run(List<List<Integer>> answers, long visited) {}

    static List<List<Integer>> permutations(int[] values) {
        List<List<Integer>> answers = new ArrayList<>();
        permute(values, new boolean[values.length], new ArrayList<>(), answers);
        return answers;
    }
    static void permute(int[] values, boolean[] used, List<Integer> path,
                        List<List<Integer>> answers) {
        if (path.size() == values.length) {
            answers.add(List.copyOf(path));
            return;
        }
        for (int i = 0; i < values.length; i++) {
            if (used[i]) continue;           // Запрещена позиция только в текущей ветке.
            used[i] = true;
            path.add(values[i]);
            permute(values, used, path, answers);
            path.remove(path.size() - 1);
            used[i] = false;                 // Следующая ветка опять вправе выбрать i.
        }
    }

    static Run combinations(int n, int k, boolean bounded) {
        List<List<Integer>> answers = new ArrayList<>();
        long[] visited = {0};
        if (k >= 0 && k <= n) combine(n, k, 1, new ArrayList<>(), answers, visited, bounded);
        return new Run(answers, visited[0]);
    }
    static void combine(int n, int k, int start, List<Integer> path,
                        List<List<Integer>> answers, long[] visited, boolean bounded) {
        visited[0]++;
        int need = k - path.size();
        if (need == 0) {
            answers.add(List.copyOf(path));
            return;
        }
        int last = bounded ? n - need + 1 : n; // После выбора i должно хватить ещё need-1 чисел.
        for (int i = start; i <= last; i++) {
            path.add(i);
            combine(n, k, i + 1, path, answers, visited, bounded);
            path.remove(path.size() - 1);
        }
    }

    public static void main(String[] args) {
        System.out.println("== order is part of a permutation ==");
        System.out.println("permutations=" + permutations(new int[]{4,7,9}));
        System.out.println("empty=" + permutations(new int[0]));

        System.out.println("== combinations grow by index ==");
        System.out.println("n=4 k=2: " + combinations(4, 2, true).answers());
        System.out.println("k=0: " + combinations(4, 0, true).answers());
        System.out.println("k>n: " + combinations(4, 5, true).answers());

        System.out.println("== prune only impossible prefixes ==");
        Run plain = combinations(8, 7, false);
        Run pruned = combinations(8, 7, true);
        System.out.println("plain visited=" + plain.visited() + " answers=" + plain.answers().size());
        System.out.println("pruned visited=" + pruned.visited() + " answers=" + pruned.answers().size());
        System.out.println("same answers=" + plain.answers().equals(pruned.answers()));
    }
}
