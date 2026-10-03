import java.util.*;

public class RoomsDemo {
    static int rooms(int[][] input, boolean trace) {
        int[][] sorted = input.clone();
        Arrays.sort(sorted, Comparator.comparingInt(a -> a[0]));
        PriorityQueue<Integer> ends = new PriorityQueue<>();
        int best = 0;
        for (int[] interval : sorted) {
            while (!ends.isEmpty() && ends.peek() <= interval[0]) ends.remove(); // [start,end): конец освобождает слот.
            ends.add(interval[1]);
            best = Math.max(best, ends.size()); // Размер после последнего старта не хранит прошлый пик.
            if (trace) {
                List<Integer> ordered = new ArrayList<>(ends); Collections.sort(ordered);
                System.out.println("start=" + interval[0] + " activeEnds=" + ordered + " best=" + best);
            }
        }
        if (trace) System.out.println("final active=" + ends.size());
        return best;
    }
    static int events(int[][] input, boolean startsFirst, boolean trace) {
        List<int[]> events = new ArrayList<>();
        for (int[] interval : input) {
            events.add(new int[]{interval[0], 1});
            events.add(new int[]{interval[1], -1});
        }
        events.sort((a, b) -> {
            int order = Integer.compare(a[0], b[0]);
            if (order != 0) return order;
            return startsFirst ? Integer.compare(b[1], a[1]) : Integer.compare(a[1], b[1]);
        }); // Для [start,end) события -1 стоят раньше +1 при одном времени.
        int active = 0, best = 0;
        for (int[] event : events) {
            active += event[1]; best = Math.max(best, active);
            if (trace) System.out.println("t=" + event[0] + " delta=" + event[1] + " active=" + active + " best=" + best);
        }
        return best;
    }

    public static void main(String[] args) {
        System.out.println("== peak differs from final heap size ==");
        int[][] input = {{0, 5}, {1, 3}, {2, 4}, {5, 7}}; // Положительная длина каждого [start,end).
        System.out.println("rooms=" + rooms(input, true));
        System.out.println("events=" + events(input, false, false));

        System.out.println("== release before arrival at the same time ==");
        int[][] touching = {{1, 3}, {3, 5}, {3, 4}};
        System.out.println("rooms=" + rooms(touching, false));
        System.out.println("ends first=" + events(touching, false, true));
        System.out.println("starts first=" + events(touching, true, false));

        System.out.println("== one covered range can need three slots ==");
        int[][] nested = {{1, 9}, {2, 8}, {3, 7}};
        System.out.println("nested rooms=" + rooms(nested, false));
        System.out.println("chain rooms=" + rooms(new int[][]{{1, 3}, {2, 4}, {3, 5}}, false));
        System.out.println("empty rooms=" + rooms(new int[0][], false));
    }
}
