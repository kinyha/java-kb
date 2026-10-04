import java.util.*;

public class IslandsDemo {
    static final int[][] FOUR = {{0,1},{1,0},{0,-1},{-1,0}};
    static final int[][] EIGHT = {{0,1},{1,0},{0,-1},{-1,0},{1,1},{1,-1},{-1,1},{-1,-1}};
    record Cell(int row, int col) {}
    record Regions(int[][] labels, List<Integer> sizes) {}

    static Regions label(int[][] grid, int[][] directions) {
        int rows = grid.length, cols = rows == 0 ? 0 : grid[0].length;
        int[][] labels = new int[rows][cols];
        List<Integer> sizes = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == 0 || labels[r][c] != 0) continue;
                int id = sizes.size() + 1, size = 0; // Один новый старт — одна компонента.
                ArrayDeque<Cell> queue = new ArrayDeque<>();
                labels[r][c] = id;                 // Отмечаем до добавления в очередь.
                queue.addLast(new Cell(r, c));
                while (!queue.isEmpty()) {
                    Cell cell = queue.removeFirst();
                    size++;
                    for (int[] d : directions) {
                        int nr = cell.row() + d[0], nc = cell.col() + d[1];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                        if (grid[nr][nc] == 0 || labels[nr][nc] != 0) continue;
                        labels[nr][nc] = id;        // Другой сосед уже не добавит эту клетку.
                        queue.addLast(new Cell(nr, nc));
                    }
                }
                sizes.add(size);
            }
        }
        return new Regions(labels, sizes);
    }

    static List<Integer> diamond(boolean early) {
        int[][] edges = {{1,2},{3},{3},{}}; // Два разных пути сходятся в 3.
        boolean[] seen = new boolean[4];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        List<Integer> popped = new ArrayList<>();
        queue.addLast(0);
        if (early) seen[0] = true;
        while (!queue.isEmpty()) {
            int v = queue.removeFirst();
            popped.add(v);                  // Считаем все извлечения, включая повторные.
            if (!early) {
                if (seen[v]) continue;      // Поздняя отметка спасает обработку, но не очередь.
                seen[v] = true;
            }
            for (int w : edges[v]) {
                if (seen[w]) continue;
                if (early) seen[w] = true;
                queue.addLast(w);
            }
        }
        return popped;
    }

    public static void main(String[] args) {
        System.out.println("== one scan, four components ==");
        int[][] grid = {{1,1,0,0,1},{0,1,0,1,1},{0,0,1,0,0},{0,0,0,1,1}};
        Regions regions = label(grid, FOUR);
        System.out.println("labels=" + Arrays.deepToString(regions.labels()));
        System.out.println("sizes=" + regions.sizes());
        System.out.println("input=" + Arrays.deepToString(grid));

        System.out.println("== adjacency changes the answer ==");
        System.out.println("four=" + label(grid, FOUR).sizes().size());
        System.out.println("eight=" + label(grid, EIGHT).sizes().size());
        System.out.println("empty=" + label(new int[0][], FOUR).sizes());
        System.out.println("water=" + label(new int[][]{{0,0}}, FOUR).sizes());

        System.out.println("== mark before enqueue ==");
        System.out.println("early=" + diamond(true));
        System.out.println("late=" + diamond(false));
    }
}
