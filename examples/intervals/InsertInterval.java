import java.util.*;

public class InsertInterval {
    static int[][] solve(int[][] input, int[] added) {
        List<int[]> result = new ArrayList<>();
        int i = 0, left = added[0], right = added[1];
        while (i < input.length && input[i][1] < left) {
            result.add(input[i++].clone()); // Левый префикс уже не пересечётся с добавленным.
        }
        while (i < input.length && input[i][0] <= right) {
            left = Math.min(left, input[i][0]);
            right = Math.max(right, input[i][1]); // Расширенный конец влияет на следующий шаг.
            i++;
        }
        result.add(new int[]{left, right});
        while (i < input.length) result.add(input[i++].clone()); // Правый суффикс не затронут.
        return result.toArray(int[][]::new);
    }

    public static void main(String[] args) {
        check(new int[][]{}, new int[]{2, 5}, new int[][]{{2, 5}});
        check(new int[][]{{1, 3}, {6, 9}}, new int[]{2, 5}, new int[][]{{1, 5}, {6, 9}});
        check(new int[][]{{1, 2}, {3, 5}, {6, 7}, {8, 10}, {12, 16}}, new int[]{4, 8}, new int[][]{{1, 2}, {3, 10}, {12, 16}});
        check(new int[][]{{3, 5}}, new int[]{1, 2}, new int[][]{{1, 2}, {3, 5}});
        check(new int[][]{{3, 5}}, new int[]{6, 7}, new int[][]{{3, 5}, {6, 7}});
        check(new int[][]{{1, 10}}, new int[]{4, 4}, new int[][]{{1, 10}});
        check(new int[][]{{1, 2}, {4, 5}}, new int[]{2, 4}, new int[][]{{1, 5}});
        check(new int[][]{{1, 2}, {4, 5}}, new int[]{-9, 9}, new int[][]{{-9, 9}});
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
    static void check(int[][] input, int[] added, int[][] want) {
        int[][] before = copy(input); int[] oldAdded = added.clone();
        int[][] got = solve(input, added);
        if (!Arrays.deepEquals(got, want)) throw new AssertionError(Arrays.deepToString(got));
        checkInput(before, input); checkFresh(got, input);
        if (!Arrays.equals(added, oldAdded)) throw new AssertionError("added changed");
        for (int[] row : got) if (row == added) throw new AssertionError("shared added");
        System.out.println("OK " + Arrays.deepToString(got));
    }
}
