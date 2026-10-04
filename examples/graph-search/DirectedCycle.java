import java.util.*;

public class DirectedCycle {
    static final class Frame {
        final int vertex;
        int nextEdge;
        Frame(int vertex) { this.vertex = vertex; }
    }

    static boolean solve(int n, int[][] edges) {
        List<List<Integer>> next = new ArrayList<>();
        for (int v = 0; v < n; v++) next.add(new ArrayList<>());
        for (int[] e : edges) next.get(e[0]).add(e[1]);
        int[] color = new int[n];
        for (int start = 0; start < n; start++) {
            if (color[start] != 0) continue;
            ArrayDeque<Frame> stack = new ArrayDeque<>();
            color[start] = 1;
            stack.push(new Frame(start));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                int v = frame.vertex;
                if (frame.nextEdge == next.get(v).size()) {
                    color[v] = 2;               // Завершение только после последнего ребра.
                    stack.pop();
                    continue;
                }
                int w = next.get(v).get(frame.nextEdge++);
                if (color[w] == 1) return true;  // Путь по стеку w ... v замыкается ребром v -> w.
                if (color[w] == 0) {
                    color[w] = 1;
                    stack.push(new Frame(w));
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {
        test(4, new int[][]{{0,1},{1,3},{0,2},{2,3}}, false);
        test(4, new int[][]{{0,1},{1,2},{2,1},{2,3}}, true);
        test(1, new int[][]{{0,0}}, true);
        test(2, new int[][]{{0,1},{0,1}}, false);
        test(4, new int[][]{{2,3},{3,2}}, true);
        test(3, new int[0][], false);
        test(0, new int[0][], false);
        int[][] chain = new int[19999][2];
        for (int i = 0; i < chain.length; i++) chain[i] = new int[]{i,i+1};
        test(20000, chain, false);           // Глубина хранится в heap, а не в стеке вызовов.
    }
    static void test(int n, int[][] edges, boolean want) {
        int[][] before = Arrays.stream(edges).map(int[]::clone).toArray(int[][]::new);
        check(solve(n, edges), want);
        if (!Arrays.deepEquals(edges, before)) throw new AssertionError("input changed");
    }

    static void check(Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError("got=" + got + " want=" + want);
        System.out.println("OK got=" + got + " want=" + want);
    }
}
