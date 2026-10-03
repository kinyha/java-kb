import java.util.*;

public class MeetingRooms {
    static int solve(int[][] input) {
        int[][] sorted = input.clone();
        Arrays.sort(sorted, Comparator.comparingInt(a -> a[0]));
        PriorityQueue<Integer> ends = new PriorityQueue<>();
        int best = 0;
        for (int[] interval : sorted) {
            while (!ends.isEmpty() && ends.peek() <= interval[0]) ends.remove(); // [start,end): конец освобождает слот.
            ends.add(interval[1]);
            best = Math.max(best, ends.size()); // Размер после последнего старта не хранит прошлый пик.

        }
        return best;
    }

    public static void main(String[] args) {
        check(new int[][]{}, 0);
        check(new int[][]{{1, 3}, {3, 5}}, 1);
        check(new int[][]{{1, 3}, {3, 5}, {3, 4}}, 2);
        check(new int[][]{{0, 5}, {1, 3}, {2, 4}, {5, 7}}, 3);
        check(new int[][]{{1, 9}, {2, 8}, {3, 7}}, 3);
        check(new int[][]{{1, 3}, {2, 4}, {3, 5}}, 2);
        check(new int[][]{{9, 10}, {-5, -1}, {-3, 0}}, 2);
        check(new int[][]{{1, 2}, {1, 2}, {1, 2}}, 3);
        check(new int[][]{{Integer.MIN_VALUE, 0}, {0, Integer.MAX_VALUE}}, 1);
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
    static void check(int[][] input, int want) {
        int[][] before = copy(input); int got = solve(input);
        if (got != want) throw new AssertionError("got=" + got + " want=" + want);
        checkInput(before, input);
        System.out.println("OK rooms=" + got + " input=" + Arrays.deepToString(input));
    }
}
