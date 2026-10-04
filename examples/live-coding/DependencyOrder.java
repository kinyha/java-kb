import java.util.*;
public class DependencyOrder {
    record Edge(int before, int after) {}
    static Optional<List<Integer>> solve(int n, Edge[] edges) {
        List<List<Integer>> next = new ArrayList<>();
        for (int i = 0; i < n; i++) next.add(new ArrayList<>());
        int[] incoming = new int[n];
        for (Edge edge : edges) {
            next.get(edge.before()).add(edge.after());
            incoming[edge.after()]++;          // повторное ребро учитывается и здесь, и при снятии
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < n; i++) if (incoming[i] == 0) ready.add(i);
        List<Integer> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int id = ready.remove();           // минимальный ID среди допустимых прямо сейчас
            order.add(id);
            for (int after : next.get(id)) {
                if (--incoming[after] == 0) ready.add(after);
            }
        }
        if (order.size() != n) return Optional.empty(); // частичный порядок скрывал бы цикл
        return Optional.of(List.copyOf(order));
    }
    static void check(String name, Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError(name + ": " + got + " != " + want);
        System.out.println(name + " OK");
    }
    public static void main(String[] args) {
        check("empty graph", solve(0, new Edge[0]), Optional.of(List.of()));
        check("independent", solve(3, new Edge[0]), Optional.of(List.of(0, 1, 2)));
        check("chain", solve(3, new Edge[]{new Edge(2, 1), new Edge(1, 0)}), Optional.of(List.of(2, 1, 0)));
        check("new minimum", solve(4, new Edge[]{new Edge(1, 0)}), Optional.of(List.of(1, 0, 2, 3)));
        check("diamond", solve(4, new Edge[]{new Edge(0, 2), new Edge(1, 2), new Edge(2, 3)}), Optional.of(List.of(0, 1, 2, 3)));
        check("duplicate edge", solve(2, new Edge[]{new Edge(0, 1), new Edge(0, 1)}), Optional.of(List.of(0, 1)));
        check("self cycle", solve(1, new Edge[]{new Edge(0, 0)}), Optional.empty());
        check("cycle after prefix", solve(3, new Edge[]{new Edge(1, 2), new Edge(2, 1)}), Optional.empty());
        Edge[] values = {new Edge(2, 1), new Edge(1, 0)};
        Edge[] before = values.clone();
        solve(3, values);
        check("input unchanged", Arrays.equals(values, before), true);
    }
}
