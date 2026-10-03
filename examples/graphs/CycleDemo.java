import java.util.*;

public class CycleDemo {
    static List<List<Integer>> graph(int n, int[][] edges) {
        var g = new ArrayList<List<Integer>>(n);
        for (int i = 0; i < n; i++) g.add(new ArrayList<>());
        for (int[] e : edges) g.get(e[0]).add(e[1]);
        return g;
    }

    static boolean dfs(List<List<Integer>> g, int v, int[] color, boolean trace) {
        color[v] = 1; // Серый: вызов начался, но ещё не завершён.
        if (trace) System.out.println("enter=" + v);
        for (int w : g.get(v)) {
            if (color[w] == 1) {
                if (trace) System.out.println("back edge=" + v + "->" + w);
                return true; // w лежит на текущем пути: найден ориентированный цикл.
            }
            if (color[w] == 0 && dfs(g, w, color, trace)) return true;
            if (trace && color[w] == 2) System.out.println("done edge=" + v + "->" + w);
        }
        color[v] = 2; // Чёрный: все исходящие рёбра обработаны.
        if (trace) System.out.println("exit=" + v);
        return false;
    }

    static boolean hasCycle(List<List<Integer>> g, boolean trace) {
        int[] color = new int[g.size()];
        for (int v = 0; v < g.size(); v++) { // Цикл может быть вне компоненты вершины 0.
            if (color[v] == 0 && dfs(g, v, color, trace)) return true;
        }
        return false;
    }

    record Frame(int vertex, Iterator<Integer> edges) {}

    static boolean iterativeCycle(List<List<Integer>> g) {
        int[] color = new int[g.size()];
        var stack = new ArrayDeque<Frame>();
        for (int start = 0; start < g.size(); start++) {
            if (color[start] != 0) continue;
            color[start] = 1;
            stack.push(new Frame(start, g.get(start).iterator()));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                if (!frame.edges().hasNext()) {
                    color[frame.vertex()] = 2; // Выход из кадра после всех детей.
                    stack.pop();
                    continue;
                }
                int w = frame.edges().next(); // Родитель запомнил, какое ребро смотреть дальше.
                if (color[w] == 1) return true;
                if (color[w] == 0) {
                    color[w] = 1;
                    stack.push(new Frame(w, g.get(w).iterator()));
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {
        var diamond = graph(4, new int[][]{{0, 1}, {0, 2}, {1, 3}, {2, 3}});
        System.out.println("== shared descendant ==");
        System.out.println("cycle=" + hasCycle(diamond, true));
        var loop = graph(4, new int[][]{{0, 1}, {0, 2}, {1, 3}, {2, 3}, {3, 1}});
        System.out.println("== back edge ==");
        System.out.println("cycle=" + hasCycle(loop, true));
        System.out.println("== disconnected and deep ==");
        var isolatedLoop = graph(4, new int[][]{{0, 1}, {2, 3}, {3, 2}});
        System.out.println("outside start component=" + hasCycle(isolatedLoop, false));
        var self = graph(1, new int[][]{{0, 0}});
        System.out.println("self loop=" + iterativeCycle(self));
        int n = 100_000;
        var chain = graph(n, new int[0][]);
        for (int v = 0; v + 1 < n; v++) chain.get(v).add(v + 1);
        System.out.println("iterative chain n=" + n + " cycle=" + iterativeCycle(chain));
        System.out.println("iterative diamond=" + iterativeCycle(diamond));
    }
}
