import java.util.*;

public class DependenciesDemo {
    static List<List<Integer>> graph(int n, int[][] edges) {
        var g = new ArrayList<List<Integer>>(n);
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());
        for (int[] e : edges) g.get(e[0]).add(e[1]);
        return g;
    }

    static List<Integer> topological(List<List<Integer>> g, boolean trace) {
        int[] in = new int[g.size()];
        for (var list : g) for (int w : list) in[w]++; // Сколько предпосылок ещё не снято.
        var ready = new ArrayDeque<Integer>();
        for (int v = 0; v < g.size(); v++) if (in[v] == 0) ready.addLast(v);
        var order = new ArrayList<Integer>();
        if (trace) System.out.println("start ready=" + ready + " in=" + Arrays.toString(in));
        while (!ready.isEmpty()) {
            int v = ready.removeFirst();
            order.add(v);
            for (int w : g.get(v)) if (--in[w] == 0) ready.addLast(w);
            if (trace) System.out.println("take=" + v + " ready=" + ready + " in=" + Arrays.toString(in));
        }
        if (order.size() != g.size()) {
            var blocked = new ArrayList<Integer>();
            for (int v = 0; v < g.size(); v++) if (in[v] > 0) blocked.add(v);
            // Остаток включает зависимые от цикла вершины, а не только сам цикл.
            throw new IllegalStateException("partial=" + order + " blocked=" + blocked);
        }
        return order;
    }

    public static void main(String[] args) {
        // 0 validation, 1 risk, 2 reserve, 3 charge, 4 receipt; 5 metrics независим.
        var dag = graph(6, new int[][]{{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}});
        System.out.println("== DAG ==");
        System.out.println("order=" + topological(dag, true));
        System.out.println("== cycle with downstream ==");
        var loop = graph(6, new int[][]{{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 4}, {3, 1}});
        try {
            topological(loop, false);
        } catch (IllegalStateException error) {
            System.out.println(error.getMessage());
        }
        System.out.println("empty=" + topological(graph(0, new int[0][]), false));
    }
}
