import java.util.*;

public class SumDemo {
    static List<List<Integer>> sum(int[] candidates, int target, boolean sorted, boolean reuse) {
        int[] values = candidates.clone();
        for (int v : values) if (v <= 0) throw new IllegalArgumentException("positive values required");
        if (sorted) Arrays.sort(values);    // Исходный массив сохраняется.
        List<List<Integer>> answers = new ArrayList<>();
        if (target >= 0) search(values, target, 0, new ArrayList<>(), answers, reuse);
        return answers;
    }
    static void search(int[] values, int remaining, int start, List<Integer> path,
                       List<List<Integer>> answers, boolean reuse) {
        if (remaining == 0) {
            answers.add(List.copyOf(path));
            return;
        }
        for (int i = start; i < values.length; i++) {
            if (values[i] > remaining) break; // break верен только на отсортированном суффиксе.
            path.add(values[i]);
            search(values, remaining - values[i], reuse ? i : i + 1, path, answers, reuse);
            path.remove(path.size() - 1);
        }
    }

    public static void main(String[] args) {
        System.out.println("== reuse changes the frontier ==");
        int[] candidates = {2,7,3};
        System.out.println("reuse=" + sum(candidates, 7, true, true));
        System.out.println("once=" + sum(candidates, 7, true, false));
        System.out.println("input=" + Arrays.toString(candidates));

        System.out.println("== break needs sorted candidates ==");
        System.out.println("unsorted=" + sum(candidates, 7, false, true));
        System.out.println("sorted=" + sum(candidates, 7, true, true));

        System.out.println("== remaining must make progress ==");
        System.out.println("zero target=" + sum(new int[]{2,3}, 0, true, true));
        System.out.println("unreachable=" + sum(new int[]{4,6}, 5, true, true));
        System.out.println("empty=" + sum(new int[0], 7, true, true));
        try {
            sum(new int[]{0,2}, 2, true, true); // Ноль с повторным выбором не уменьшил бы remaining.
        } catch (IllegalArgumentException e) {
            System.out.println("zero candidate=" + e.getMessage());
        }
    }
}
