import java.util.*;

public class MergeIntervals {
    static int[][] solve(int[][] input) {
        int[][] sorted = input.clone(); // Копируем порядок ссылок; строки ниже только читаем.
        Arrays.sort(sorted, Comparator.comparingInt(a -> a[0]));
        List<int[]> result = new ArrayList<>();
        for (int[] next : sorted) {
            if (result.isEmpty() || next[0] > result.getLast()[1]) {
                result.add(next.clone()); // Новый компонент; ответ не разделяет строки со входом.
            } else {
                int[] current = result.getLast();
                current[1] = Math.max(current[1], next[1]); // Вложенный интервал не сокращает конец.
            }
        }
        return result.toArray(int[][]::new);
    }

    public static void main(String[] args) {
        check(new int[][]{}, new int[][]{});
        check(new int[][]{{1, 3}, {3, 5}}, new int[][]{{1, 5}});
        check(new int[][]{{8, 10}, {2, 3}, {1, 7}, {6, 9}}, new int[][]{{1, 10}});
        check(new int[][]{{1, 10}, {2, 3}, {9, 12}}, new int[][]{{1, 12}});
        check(new int[][]{{5, 5}, {1, 1}}, new int[][]{{1, 1}, {5, 5}});
        check(new int[][]{{2, 4}, {2, 4}, {2, 2}}, new int[][]{{2, 4}});
        check(new int[][]{{-9, -4}, {-5, 0}, {8, 9}}, new int[][]{{-9, 0}, {8, 9}});
        check(new int[][]{{Integer.MAX_VALUE, Integer.MAX_VALUE}, {Integer.MIN_VALUE, Integer.MIN_VALUE}},
              new int[][]{{Integer.MIN_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}});
    }
    static int[][] copy(int[][] input) {
        return Arrays.stream(input).map(int[]::clone).toArray(int[][]::new);
    }
    static void checkInput(int[][] before, int[][] input) {
        if (!Arrays.deepEquals(before, input)) throw new AssertionError("input changed");
    }
    static void checkFresh(int[][] result, int[][] input) {
        for (int[] row : result) for (int[] old : input)
            if (row == old) throw new AssertionError("shared row");
    }
    static void check(int[][] input, int[][] want) {
        int[][] before = copy(input), got = solve(input);
        if (!Arrays.deepEquals(got, want)) throw new AssertionError(Arrays.deepToString(got));
        checkInput(before, input); checkFresh(got, input);
        System.out.println("OK " + Arrays.deepToString(got));
    }
}
