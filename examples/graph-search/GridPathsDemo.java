import java.util.*;

public class GridPathsDemo {
    static final int[][] FOUR = {{0,1},{1,0},{0,-1},{-1,0}};
    static final int[][] EIGHT = {{0,1},{1,0},{0,-1},{-1,0},{1,1},{1,-1},{-1,1},{-1,-1}};
    record Cell(int row, int col) {
        public String toString() { return "(" + row + "," + col + ")"; }
    }
    record Search(int[][] dist, Cell[][] parent) {}

    static Search bfs(int[][] grid, Cell source, int[][] directions, boolean trace) {
        int rows = grid.length, cols = rows == 0 ? 0 : grid[0].length;
        int[][] dist = new int[rows][cols];
        for (int[] row : dist) Arrays.fill(row, -1);
        Cell[][] parent = new Cell[rows][cols];
        if (rows == 0 || cols == 0 || grid[source.row()][source.col()] != 0)
            return new Search(dist, parent);
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        dist[source.row()][source.col()] = 0; // Расстояние в рёбрах: до самого себя ноль.
        queue.addLast(source);
        int level = 0;
        while (!queue.isEmpty()) {
            int width = queue.size();         // В этой порции только клетки одного уровня.
            if (trace) System.out.println("level=" + level + " queue=" + queue);
            for (int i = 0; i < width; i++) {
                Cell v = queue.removeFirst();
                for (int[] d : directions) {
                    int nr = v.row() + d[0], nc = v.col() + d[1];
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                    if (grid[nr][nc] != 0 || dist[nr][nc] != -1) continue;
                    dist[nr][nc] = dist[v.row()][v.col()] + 1;
                    parent[nr][nc] = v;        // Первый путь уже кратчайший: не перезаписываем.
                    queue.addLast(new Cell(nr, nc));
                }
            }
            level++;
        }
        return new Search(dist, parent);
    }

    static List<Cell> path(Search search, Cell target) {
        if (search.dist()[target.row()][target.col()] == -1) return List.of();
        List<Cell> result = new ArrayList<>();
        for (Cell v = target; v != null; v = search.parent()[v.row()][v.col()]) result.add(v);
        Collections.reverse(result); // Родители ведут от цели к старту.
        return result;
    }

    public static void main(String[] args) {
        System.out.println("== equal-cost layers ==");
        int[][] grid = {{0,0,0,0},{0,1,1,0},{0,0,0,0}};
        Search search = bfs(grid, new Cell(0,0), FOUR, true);
        System.out.println("dist=" + Arrays.deepToString(search.dist()));
        System.out.println("path=" + path(search, new Cell(2,3)));
        System.out.println("edges=" + search.dist()[2][3]);

        System.out.println("== four or eight moves ==");
        int[][] diagonal = {{0,1},{1,0}};
        System.out.println("four edges=" + bfs(diagonal, new Cell(0,0), FOUR, false).dist()[1][1]);
        Search eight = bfs(diagonal, new Cell(0,0), EIGHT, false);
        System.out.println("eight edges=" + eight.dist()[1][1]);
        System.out.println("eight cells=" + path(eight, new Cell(1,1)).size());

        System.out.println("== one cell or blocked source ==");
        Search one = bfs(new int[][]{{0}}, new Cell(0,0), FOUR, false);
        System.out.println("one edges=" + one.dist()[0][0] + " cells=" + path(one, new Cell(0,0)).size());
        Search blocked = bfs(new int[][]{{1}}, new Cell(0,0), FOUR, false);
        System.out.println("blocked edges=" + blocked.dist()[0][0] + " path=" + path(blocked, new Cell(0,0)));
    }
}
