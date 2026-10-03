import java.util.*;

public class GraphWalkDemo {
    static List<List<Integer>> graph(int n, int[][] edges) {
        var g = new ArrayList<List<Integer>>(n);
        for (int i = 0; i < n; i++) g.add(new ArrayList<>()); // Изолированные вершины тоже существуют.
        for (int[] e : edges) g.get(e[0]).add(e[1]); // Ориентированное ребро: только from -> to.
        return g;
    }

    record Search(int[] dist, int[] parent, List<Integer> order) {}

    static Search bfs(List<List<Integer>> g, int start, boolean trace) {
        int[] dist = new int[g.size()], parent = new int[g.size()];
        Arrays.fill(dist, -1); // -1 означает «ещё не обнаружен».
        Arrays.fill(parent, -1);
        var queue = new ArrayDeque<Integer>();
        var order = new ArrayList<Integer>();
        dist[start] = 0;
        queue.addLast(start);
        if (trace) System.out.println("start queue=" + queue + " dist=" + Arrays.toString(dist));
        while (!queue.isEmpty()) {
            int v = queue.removeFirst(); // FIFO: сначала все более близкие вершины.
            order.add(v);
            for (int w : g.get(v)) {
                if (dist[w] != -1) continue;
                dist[w] = dist[v] + 1; // Пометка при добавлении исключает повторы в очереди.
                parent[w] = v;
                queue.addLast(w);
            }
            if (trace) System.out.println("take=" + v + " queue=" + queue + " dist=" + Arrays.toString(dist));
        }
        return new Search(dist, parent, order);
    }

    static List<Integer> path(Search search, int target) {
        if (search.dist()[target] == -1) return List.of();
        var path = new ArrayList<Integer>();
        for (int v = target; v != -1; v = search.parent()[v]) path.add(v);
        Collections.reverse(path); // parent хранит путь в обратном направлении.
        return path;
    }

    public static void main(String[] args) {
        // 0 gateway, 1 bank, 2 risk, 3 settlement, 4 audit, 5 archive.
        int[][] edges = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}, {4, 1}};
        var g = graph(6, edges);
        boolean[][] matrix = new boolean[g.size()][g.size()];
        for (int[] e : edges) matrix[e[0]][e[1]] = true;
        System.out.println("== representation ==");
        for (int v = 0; v < g.size(); v++) System.out.println(v + " -> " + g.get(v));
        System.out.println("0->1=" + matrix[0][1] + " 1->0=" + matrix[1][0]);
        System.out.println("vertices=" + g.size() + " edges=" + edges.length);
        System.out.println("== BFS ==");
        Search search = bfs(g, 0, true);
        System.out.println("order=" + search.order());
        System.out.println("path 4=" + path(search, 4));
        System.out.println("path 5=" + path(search, 5));
        System.out.println("path 0=" + path(search, 0));
        System.out.println("== reverse reachability ==");
        Search reverseStart = bfs(g, 4, false);
        System.out.println("from 4 to 0=" + path(reverseStart, 0));
        System.out.println("from 4 to 3=" + path(reverseStart, 3));
    }
}
