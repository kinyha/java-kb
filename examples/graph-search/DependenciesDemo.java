import java.util.*;

public class DependenciesDemo {
    record Order(List<Integer> removed, List<Integer> blocked) {}

    static List<List<Integer>> graph(int n, int[][] edges) {
        List<List<Integer>> next = new ArrayList<>();
        for (int v = 0; v < n; v++) next.add(new ArrayList<>());
        for (int[] e : edges) next.get(e[0]).add(e[1]); // Предпосылка -> зависимая задача.
        return next;
    }

    static Order kahn(int n, int[][] edges, boolean trace) {
        List<List<Integer>> next = graph(n, edges);
        int[] need = new int[n];
        for (int[] e : edges) need[e[1]]++;
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int v = 0; v < n; v++) if (need[v] == 0) ready.addLast(v);
        List<Integer> order = new ArrayList<>();
        if (trace) System.out.println("start ready=" + ready + " need=" + Arrays.toString(need));
        while (!ready.isEmpty()) {
            int v = ready.removeFirst();
            order.add(v);
            for (int w : next.get(v)) {
                if (--need[w] == 0) ready.addLast(w); // Только последняя предпосылка открывает w.
            }
            if (trace) System.out.println("remove=" + v + " ready=" + ready + " need=" + Arrays.toString(need));
        }
        List<Integer> blocked = new ArrayList<>();
        for (int v = 0; v < n; v++) if (need[v] != 0) blocked.add(v);
        return new Order(order, blocked); // Остаток включает потомков цикла, а не только сам цикл.
    }

    static final class Frame {
        final int vertex;
        int nextEdge;
        Frame(int vertex) { this.vertex = vertex; }
    }

    static List<Integer> cycle(int n, int[][] edges) {
        List<List<Integer>> next = graph(n, edges);
        int[] color = new int[n], parent = new int[n];
        Arrays.fill(parent, -1);
        for (int start = 0; start < n; start++) {
            if (color[start] != 0) continue; // Проверяем и отдельные компоненты.
            ArrayDeque<Frame> stack = new ArrayDeque<>();
            color[start] = 1;
            stack.push(new Frame(start));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                int v = frame.vertex;
                if (frame.nextEdge == next.get(v).size()) {
                    color[v] = 2;          // Все рёбра проверены: ветка действительно завершена.
                    stack.pop();
                    continue;
                }
                int w = next.get(v).get(frame.nextEdge++); // Сохраняем место возврата в v.
                if (color[w] == 1) {
                    List<Integer> found = new ArrayList<>();
                    for (int x = v; x != w; x = parent[x]) found.add(x);
                    found.add(w);
                    Collections.reverse(found);
                    found.add(w);          // Последнее ребро v -> w замыкает путь.
                    return found;
                }
                if (color[w] == 0) {
                    parent[w] = v;
                    color[w] = 1;
                    stack.push(new Frame(w));
                }                         // color=2: уже завершённая ветка, цикла не доказывает.
            }
        }
        return List.of();
    }

    public static void main(String[] args) {
        System.out.println("== all prerequisites must be removed ==");
        int[][] edges = {{0,2},{1,2},{2,3},{2,4},{3,5},{4,5}};
        Order order = kahn(6, edges, true);
        System.out.println("order=" + order.removed() + " blocked=" + order.blocked());

        System.out.println("== blocked is larger than the cycle ==");
        int[][] bad = {{0,1},{1,2},{2,1},{2,3}}; // 3 ждёт цикл, но сама в цикл не входит.
        Order partial = kahn(5, bad, false);  // Вершина 4 отдельная и сразу готова.
        System.out.println("removed=" + partial.removed() + " blocked=" + partial.blocked());
        System.out.println("cycle=" + cycle(5, bad));

        System.out.println("== a finished branch is not a cycle ==");
        System.out.println("diamond=" + cycle(4, new int[][]{{0,1},{1,3},{0,2},{2,3}}));
        System.out.println("self=" + cycle(1, new int[][]{{0,0}}));
        System.out.println("duplicate=" + kahn(2, new int[][]{{0,1},{0,1}}, false).removed());
        System.out.println("disconnected=" + cycle(4, new int[][]{{2,3},{3,2}}));
    }
}
