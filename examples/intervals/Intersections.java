import java.util.*;

public class Intersections {
    static int[][] solve(int[][] a, int[][] b) {
        List<int[]> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.length && j < b.length) {
            int left = Math.max(a[i][0], b[j][0]);
            int right = Math.min(a[i][1], b[j][1]);
            if (left <= right) result.add(new int[]{left, right}); // Для закрытых границ точка тоже ответ.
            if (a[i][1] < b[j][1]) i++; // Раньше закончившийся уже не встретит следующий из другого списка.
            else if (a[i][1] > b[j][1]) j++;
            else { i++; j++; } // Оба конца исчерпаны.
        }
        return result.toArray(int[][]::new);
    }

    public static void main(String[] args) {
        check(new int[][]{{0, 2}, {5, 10}, {13, 16}}, new int[][]{{1, 5}, {8, 12}, {15, 18}},
              new int[][]{{1, 2}, {5, 5}, {8, 10}, {15, 16}});
        check(new int[][]{{1, 3}}, new int[][]{{3, 5}}, new int[][]{{3, 3}});
        check(new int[][]{}, new int[][]{{1, 2}}, new int[][]{});
        check(new int[][]{{1, 2}}, new int[][]{}, new int[][]{});
        check(new int[][]{{1, 2}, {5, 6}}, new int[][]{{3, 4}}, new int[][]{});
        check(new int[][]{{1, 9}}, new int[][]{{2, 3}, {5, 6}}, new int[][]{{2, 3}, {5, 6}});
        check(new int[][]{{1, 2}, {4, 5}}, new int[][]{{0, 2}, {4, 5}}, new int[][]{{1, 2}, {4, 5}});
        check(new int[][]{{Integer.MIN_VALUE, Integer.MAX_VALUE}}, new int[][]{{-1, 1}}, new int[][]{{-1, 1}});
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
    static void check(int[][] a, int[][] b, int[][] want) {
        int[][] oldA = copy(a), oldB = copy(b), got = solve(a, b);
        if (!Arrays.deepEquals(got, want)) throw new AssertionError(Arrays.deepToString(got));
        checkInput(oldA, a); checkInput(oldB, b); checkFresh(got, a); checkFresh(got, b);
        System.out.println("OK " + Arrays.deepToString(got));
    }
}
