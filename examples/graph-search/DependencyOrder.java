import java.util.*;

public class DependencyOrder {
    static int[] solve(int n, int[][] edges) {
        List<List<Integer>> next = new ArrayList<>();
        for (int v = 0; v < n; v++) next.add(new ArrayList<>());
        int[] need = new int[n];
        for (int[] e : edges) {
            next.get(e[0]).add(e[1]);     // e[0] надо выполнить до e[1].
            need[e[1]]++;
        }
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int v = 0; v < n; v++) if (need[v] == 0) ready.addLast(v);
        int[] order = new int[n];
        int size = 0;
        while (!ready.isEmpty()) {
            int v = ready.removeFirst();
            order[size++] = v;
            for (int w : next.get(v)) if (--need[w] == 0) ready.addLast(w);
        }
        return size == n ? order : new int[0]; // Частичный порядок не решает всю задачу.
    }

    public static void main(String[] args) {
        test(6, new int[][]{{0,2},{1,2},{2,3},{2,4},{3,5},{4,5}}, true);
        test(4, new int[][]{{0,1},{0,2},{1,3},{2,3}}, true);
        test(4, new int[][]{{2,3}}, true);
        test(3, new int[0][], true);
        test(0, new int[0][], true);
        test(1, new int[][]{{0,0}}, false);
        test(4, new int[][]{{0,1},{1,2},{2,1},{2,3}}, false);
        test(2, new int[][]{{0,1},{0,1}}, true);
        test(4, new int[][]{{2,3},{3,2}}, false);
    }
    static void test(int n, int[][] edges, boolean possible) {
        int[][] before = Arrays.stream(edges).map(int[]::clone).toArray(int[][]::new);
        int[] order = solve(n, edges);
        boolean valid = valid(n, edges, order);
        check(valid, possible);           // Проверяем свойство, а не единственный массив ответа.
        if (!possible && order.length != 0) throw new AssertionError("partial order returned");
        if (!Arrays.deepEquals(edges, before)) throw new AssertionError("input changed");
    }
    static boolean valid(int n, int[][] edges, int[] order) {
        if (order.length != n) return false;
        int[] position = new int[n];
        Arrays.fill(position, -1);
        for (int i = 0; i < n; i++) {
            int v = order[i];
            if (v < 0 || v >= n || position[v] != -1) return false;
            position[v] = i;
        }
        for (int[] e : edges) if (position[e[0]] >= position[e[1]]) return false;
        return true;
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got=" + got + " want=" + want);
        System.out.println("OK got=" + got + " want=" + want);
    }
}
