import java.util.*;

public class WeightedRoutesDemo {
    record Edge(int to, long weight) {}
    record Entry(int vertex, long distance) {} // Неизменяемый снимок, не ссылка на dist[].
    record Routes(long[] dist, int[] parent) {}
    static final long INF = Long.MAX_VALUE;

    static List<List<Edge>> graph(int n, long[][] edges) {
        var g = new ArrayList<List<Edge>>(n);
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());
        for (long[] e : edges) g.get((int) e[0]).add(new Edge((int) e[1], e[2]));
        return g;
    }

    static Routes dijkstra(List<List<Edge>> g, int start, boolean trace) {
        for (var list : g) for (Edge e : list) {
            if (e.weight() < 0) throw new IllegalArgumentException("negative weight");
        }
        long[] dist = new long[g.size()];
        int[] parent = new int[g.size()];
        Arrays.fill(dist, INF);
        Arrays.fill(parent, -1);
        var queue = new PriorityQueue<Entry>(Comparator.comparingLong(Entry::distance)
                .thenComparingInt(Entry::vertex)); // Сравниваем без вычитания и переполнения.
        dist[start] = 0;
        queue.offer(new Entry(start, 0));
        while (!queue.isEmpty()) {
            Entry current = queue.poll();
            int v = current.vertex();
            if (current.distance() != dist[v]) {
                if (trace) System.out.println("stale=" + v + " saved=" + current.distance() + " best=" + dist[v]);
                continue; // Старое расстояние больше найденного позже.
            }
            if (trace) System.out.println("settle=" + v + " distance=" + dist[v]);
            for (Edge e : g.get(v)) {
                long next = Math.addExact(dist[v], e.weight()); // Переполнение не станет «коротким путём».
                if (next == INF) throw new ArithmeticException("distance equals INF");
                if (next < dist[e.to()]) {
                    dist[e.to()] = next; // Релаксация: новый путь улучшает известный.
                    parent[e.to()] = v;
                    queue.offer(new Entry(e.to(), next)); // Старую запись не ищем и не меняем.
                    if (trace) System.out.println("relax=" + v + "->" + e.to() + " distance=" + next);
                }
            }
        }
        return new Routes(dist, parent);
    }

    static List<Integer> path(Routes routes, int target) {
        if (routes.dist()[target] == INF) return List.of();
        var path = new ArrayList<Integer>();
        for (int v = target; v != -1; v = routes.parent()[v]) path.add(v);
        Collections.reverse(path);
        return path;
    }

    static List<Integer> fewestEdges(List<List<Edge>> g, int start, int target) {
        boolean[] seen = new boolean[g.size()];
        int[] parent = new int[g.size()];
        Arrays.fill(parent, -1);
        var queue = new ArrayDeque<Integer>();
        seen[start] = true;
        queue.addLast(start);
        while (!queue.isEmpty()) {
            int v = queue.removeFirst();
            for (Edge e : g.get(v)) if (!seen[e.to()]) {
                seen[e.to()] = true; // Правильно для числа рёбер, не для суммы весов.
                parent[e.to()] = v;
                queue.addLast(e.to());
            }
        }
        if (!seen[target]) return List.of();
        var path = new ArrayList<Integer>();
        for (int v = target; v != -1; v = parent[v]) path.add(v);
        Collections.reverse(path);
        return path;
    }

    public static void main(String[] args) {
        // 0 gateway, 1 bank, 2 risk, 3 settlement, 4 archive.
        var g = graph(5, new long[][]{{0, 1, 6}, {0, 2, 1}, {0, 3, 10}, {2, 1, 1}, {1, 3, 1}});
        System.out.println("== edges and weights ==");
        System.out.println("fewest edges=" + fewestEdges(g, 0, 3));
        System.out.println("== Dijkstra ==");
        Routes routes = dijkstra(g, 0, true);
        String[] values = Arrays.stream(routes.dist()).mapToObj(d -> d == INF ? "INF" : Long.toString(d)).toArray(String[]::new);
        System.out.println("dist=" + Arrays.toString(values));
        System.out.println("path 3=" + path(routes, 3));
        System.out.println("path 4=" + path(routes, 4));
        System.out.println("== boundaries ==");
        var zero = graph(3, new long[][]{{0, 1, 0}, {1, 0, 0}, {1, 2, 2}});
        System.out.println("zero cycle dist=" + Arrays.toString(dijkstra(zero, 0, false).dist()));
        try {
            dijkstra(graph(2, new long[][]{{0, 1, -1}}), 0, false);
        } catch (IllegalArgumentException error) {
            System.out.println("negative=" + error.getMessage());
        }
        try {
            dijkstra(graph(3, new long[][]{{0, 1, Long.MAX_VALUE - 5}, {1, 2, 10}}), 0, false);
        } catch (ArithmeticException error) {
            System.out.println("overflow=" + error.getClass().getSimpleName());
        }
    }
}
