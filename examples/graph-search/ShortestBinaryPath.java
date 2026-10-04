import java.util.*;

public class ShortestBinaryPath {
    static int solve(int[][] grid) {
        int n = grid.length;
        if (n == 0 || grid[0][0] != 0 || grid[n-1][n-1] != 0) return -1;
        int[][] dist = new int[n][n];
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        dist[0][0] = 1;                   // Здесь длина в клетках: старт тоже входит в путь.
        queue.addLast(new int[]{0,0});
        while (!queue.isEmpty()) {
            int[] v = queue.removeFirst();
            if (v[0] == n-1 && v[1] == n-1) return dist[v[0]][v[1]];
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int r = v[0] + dr, c = v[1] + dc;
                    if (r < 0 || r >= n || c < 0 || c >= n) continue;
                    if (grid[r][c] != 0 || dist[r][c] != -1) continue;
                    dist[r][c] = dist[v[0]][v[1]] + 1; // Отметка одновременно с расстоянием.
                    queue.addLast(new int[]{r,c});
                }
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        test(new int[][]{{0,1},{1,0}}, 2);
        test(new int[][]{{0,0,0},{1,1,0},{1,1,0}}, 4);
        test(new int[][]{{0}}, 1);
        test(new int[][]{{1}}, -1);
        test(new int[][]{{0,0},{0,1}}, -1);
        test(new int[][]{{0,1,1},{1,1,1},{1,1,0}}, -1);
        test(new int[][]{{0,0,0},{0,0,0},{0,0,0}}, 3);
        test(new int[0][], -1);
    }
    static void test(int[][] grid, int want) {
        int[][] before = Arrays.stream(grid).map(int[]::clone).toArray(int[][]::new);
        check(solve(grid), want);
        if (!Arrays.deepEquals(grid, before)) throw new AssertionError("input changed");
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got=" + got + " want=" + want);
        System.out.println("OK got=" + got + " want=" + want);
    }
}
