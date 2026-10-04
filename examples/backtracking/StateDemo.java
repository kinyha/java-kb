import java.util.*;

public class StateDemo {
    static List<List<Integer>> subsets(int[] values, boolean snapshot, boolean trace) {
        List<List<Integer>> result = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        visit(values, 0, path, result, snapshot, trace);
        return result;
    }
    static void visit(int[] values, int start, List<Integer> path,
                      List<List<Integer>> result, boolean snapshot, boolean trace) {
        result.add(snapshot ? List.copyOf(path) : path); // Копия состояния или одна общая ссылка.
        if (trace) System.out.println("save=" + path + " start=" + start);
        for (int i = start; i < values.length; i++) {
            path.add(values[i]);                        // Сделали выбор.
            visit(values, i + 1, path, result, snapshot, trace);
            path.remove(path.size() - 1);               // Вернули состояние родителя.
        }
    }
    public static void main(String[] args) {
        System.out.println("== choose, descend, undo ==");
        int[] values = {4,7};
        List<List<Integer>> good = subsets(values, true, true);
        System.out.println("subsets=" + good);
        System.out.println("input=" + Arrays.toString(values));

        System.out.println("== an answer is a snapshot ==");
        List<List<Integer>> bad = subsets(values, false, false);
        System.out.println("bad=" + bad);
        System.out.println("same reference=" + (bad.get(0) == bad.get(1)));
        System.out.println("good=" + good);
        try {
            good.get(1).add(99); // List.copyOf возвращает неизменяемый снимок списка.
        } catch (UnsupportedOperationException e) {
            System.out.println("snapshot=" + e.getClass().getSimpleName());
        }

        System.out.println("== an empty choice is still one answer ==");
        System.out.println("empty=" + subsets(new int[0], true, false));
        System.out.println("three count=" + subsets(new int[]{4,7,9}, true, false).size());
    }
}
