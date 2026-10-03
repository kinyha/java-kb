import java.util.*;

public class RangeQueries {
    static long[] solve(int[] values, int[][] queries) {
        long[] p = new long[values.length + 1];
        for (int i = 0; i < values.length; i++) p[i + 1] = p[i] + values[i];
        long[] result = new long[queries.length];
        for (int i = 0; i < queries.length; i++) {
            int left = queries[i][0], right = queries[i][1];
            result[i] = p[right] - p[left]; // Оба индекса обозначают границы, right не входит в отрезок.
        }
        return result;
    }

    public static void main(String[] args) {
        test(new int[]{10, -4, 7, 0, -3, 5}, new int[][]{{1, 5}, {2, 6}, {0, 6}, {3, 3}, {4, 5}}, new long[]{0, 9, 15, 0, -3});
        test(new int[]{7}, new int[][]{{0, 1}, {0, 0}, {1, 1}}, new long[]{7, 0, 0});
        test(new int[0], new int[][]{{0, 0}}, new long[]{0});
        test(new int[]{1, 2}, new int[0][2], new long[0]);
        test(new int[]{-5, -2, -1}, new int[][]{{0, 3}, {1, 3}}, new long[]{-8, -3});
        test(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, new int[][]{{0, 2}}, new long[]{4294967294L});
        test(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE}, new int[][]{{0, 2}}, new long[]{-4294967296L});
        test(new int[]{0, 0}, new int[][]{{0, 2}, {2, 2}}, new long[]{0, 0});
    }

    static void test(int[] input, int[][] queries, long[] want) {
        int[] before = input.clone();
        int[][] ranges = Arrays.stream(queries).map(int[]::clone).toArray(int[][]::new);
        long[] got = solve(input, queries);
        if (!Arrays.equals(got, want)) throw new AssertionError("got " + Arrays.toString(got));
        if (!Arrays.equals(input, before) || !Arrays.deepEquals(queries, ranges)) throw new AssertionError("input changed");
        System.out.println("OK " + Arrays.toString(got));
    }
}
