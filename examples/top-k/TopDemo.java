import java.util.*;

public class TopDemo {
    static PriorityQueue<Integer> select(int[] values, int k, boolean trace) {
        if (k < 0 || k > values.length) throw new IllegalArgumentException("k outside [0,n]");
        PriorityQueue<Integer> kept = new PriorityQueue<>(); // Минимум — худший из K наибольших.
        if (k == 0) return kept; // Не читаем peek у пустой кучи.
        for (int value : values) {
            String action;
            if (kept.size() < k) { kept.add(value); action = "fill"; }
            else if (value > kept.peek()) { kept.remove(); kept.add(value); action = "replace"; }
            else action = "skip"; // Существующий равный элемент уже даёт тот же top-K значений.
            if (trace) {
                List<Integer> ordered = new ArrayList<>(kept); Collections.sort(ordered);
                System.out.println("value=" + value + " action=" + action + " kept=" + ordered + " root=" + kept.peek());
            }
        }
        return kept;
    }
    static List<Integer> descending(PriorityQueue<Integer> heap) {
        List<Integer> result = new ArrayList<>(heap); result.sort(Comparator.reverseOrder()); return result;
    }

    public static void main(String[] args) {
        System.out.println("== root is the weakest survivor ==");
        int[] values = {4, 1, 9, 2, 8, 3};
        PriorityQueue<Integer> kept = select(values, 3, true);
        System.out.println("top3=" + descending(kept) + " third largest=" + kept.peek());
        System.out.println("input=" + Arrays.toString(values));

        System.out.println("== positions count duplicates ==");
        int[] repeated = {9, 9, 8, 7};
        System.out.println("third largest=" + select(repeated, 3, false).peek());
        int[] distinct = Arrays.stream(repeated).distinct().toArray();
        System.out.println("third distinct=" + select(distinct, 3, false).peek());
        System.out.println("top all=" + descending(select(repeated, repeated.length, false)));

        System.out.println("== zero has no cutoff ==");
        System.out.println("top0=" + descending(select(values, 0, false)));
        System.out.println("empty top0=" + descending(select(new int[0], 0, false)));
        System.out.println("extremes=" + descending(select(new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE}, 2, false)));
        try { select(values, 7, false); }
        catch (IllegalArgumentException e) { System.out.println("invalid=" + e.getClass().getSimpleName()); }
    }
}
