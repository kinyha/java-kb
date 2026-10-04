import java.util.*;

public class MergeStreams {
    record Cursor(int value, int source, int index) {}
    static final Comparator<Cursor> ORDER = Comparator.comparingInt(Cursor::value)
        .thenComparingInt(Cursor::source).thenComparingInt(Cursor::index); // Равные значения: порядок источников.
    static List<Integer> solve(int[][] sources) {
        PriorityQueue<Cursor> heads = new PriorityQueue<>(ORDER);
        for (int source = 0; source < sources.length; source++)
            if (sources[source].length != 0) heads.add(new Cursor(sources[source][0], source, 0));
        List<Integer> result = new ArrayList<>();
        while (!heads.isEmpty()) {
            Cursor head = heads.remove(); result.add(head.value());
            int next = head.index() + 1;
            if (next < sources[head.source()].length)
                heads.add(new Cursor(sources[head.source()][next], head.source(), next)); // Продвинули только победивший источник.

        }
        return result;
    }

    public static void main(String[] args) {
        check(new int[][]{{1, 4, 9}, {1, 3, 8}, {}, {2, 6}}, List.of(1, 1, 2, 3, 4, 6, 8, 9));
        check(new int[0][], List.of());
        check(new int[][]{{}, {}}, List.of());
        check(new int[][]{{-2, -2, 7}}, List.of(-2, -2, 7));
        check(new int[][]{{}, {1}, {}, {1}}, List.of(1, 1));
        check(new int[][]{{Integer.MIN_VALUE, 0}, {Integer.MAX_VALUE}}, List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE));
        check(new int[][]{{-9, -1}, {-8, -2}, {-7}}, List.of(-9, -8, -7, -2, -1));
        check(new int[][]{{1, 1}, {1, 1}, {1}}, List.of(1, 1, 1, 1, 1));
    }
    static void check(int[][] sources, List<Integer> want) {
        int[][] before = Arrays.stream(sources).map(int[]::clone).toArray(int[][]::new);
        List<Integer> got = solve(sources);
        if (!got.equals(want) || !Arrays.deepEquals(sources, before)) throw new AssertionError("wrong merge or changed input");
        System.out.println("OK " + got);
    }
}
