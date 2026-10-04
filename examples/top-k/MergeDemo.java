import java.util.*;

public class MergeDemo {
    record Cursor(int value, int source, int index) {}
    static final Comparator<Cursor> ORDER = Comparator.comparingInt(Cursor::value)
        .thenComparingInt(Cursor::source).thenComparingInt(Cursor::index); // Равные значения: порядок источников.
    static List<Integer> merge(int[][] sources, boolean trace) {
        PriorityQueue<Cursor> heads = new PriorityQueue<>(ORDER);
        for (int source = 0; source < sources.length; source++)
            if (sources[source].length != 0) heads.add(new Cursor(sources[source][0], source, 0));
        List<Integer> result = new ArrayList<>();
        while (!heads.isEmpty()) {
            Cursor head = heads.remove(); result.add(head.value());
            int next = head.index() + 1;
            if (next < sources[head.source()].length)
                heads.add(new Cursor(sources[head.source()][next], head.source(), next)); // Продвинули только победивший источник.
            if (trace) {
                List<Cursor> ordered = new ArrayList<>(heads); ordered.sort(ORDER);
                System.out.println("take=" + head.value() + " source=" + head.source() + " index=" + head.index() + " heads=" + ordered);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("== one head per unfinished source ==");
        int[][] sources = {{1, 4, 9}, {1, 3, 8}, {}, {2, 6}};
        System.out.println("merged=" + merge(sources, true));
        System.out.println("input=" + Arrays.deepToString(sources));

        System.out.println("== empty sources do not create heads ==");
        System.out.println("all empty=" + merge(new int[][]{{}, {}}, false));
        System.out.println("one source=" + merge(new int[][]{{-2, -2, 7}}, false));
        System.out.println("extremes=" + merge(new int[][]{{Integer.MIN_VALUE, 0}, {Integer.MAX_VALUE}}, false));

        System.out.println("== an unsorted source hides a smaller value ==");
        System.out.println("invalid=" + merge(new int[][]{{5, 1}, {2, 3}}, false));
    }
}
