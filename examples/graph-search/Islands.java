import java.util.*;

public class Islands {
    static int solve(int[][] grid) {
        int rows = grid.length, cols = rows == 0 ? 0 : grid[0].length;
        boolean[][] seen = new boolean[rows][cols];
        int[][] directions = {{0,1},{1,0},{0,-1},{-1,0}};
        int count = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == 0 || seen[r][c]) continue;
                count++;                   // Считаем старт компоненты, а не каждую клетку.
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                seen[r][c] = true;
                queue.addLast(new int[]{r,c});
                while (!queue.isEmpty()) {
                    int[] v = queue.removeFirst();
                    for (int[] d : directions) {
                        int nr = v[0] + d[0], nc = v[1] + d[1];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                        if (grid[nr][nc] == 0 || seen[nr][nc]) continue;
                        seen[nr][nc] = true; // Один раз ставим в очередь, без изменения grid.
                        queue.addLast(new int[]{nr,nc});
                    }
                }
            }
        }
        return count;
    }

    public static void main(String[] args) {
        test(new int[][]{{1,1,0,0,1},{0,1,0,1,1},{0,0,1,0,0},{0,0,0,1,1}}, 4);
        test(new int[][]{{1,0},{0,1}}, 2);
        test(new int[][]{{1,1},{1,1}}, 1);
        test(new int[][]{{0,0},{0,0}}, 0);
        test(new int[0][], 0);
        test(new int[][]{{}}, 0);
        test(new int[][]{{1}}, 1);
        test(new int[][]{{1,0,1,0,1}}, 3);
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
