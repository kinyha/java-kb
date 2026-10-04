import java.util.*;

public class MedianPrefixes {
    static double[] solve(int[] values) {
        PriorityQueue<Integer> low = new PriorityQueue<>(Comparator.reverseOrder());
        PriorityQueue<Integer> high = new PriorityQueue<>();
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            if (low.isEmpty() || value <= low.peek()) low.add(value); // Новое число выбирает половину по её границе.
            else high.add(value);
            if (low.size() > high.size() + 1) high.add(low.remove());
            else if (low.size() < high.size()) low.add(high.remove()); // При нечётной длине лишний элемент остаётся в low.
            result[i] = low.size() == high.size()
                ? ((long) low.peek() + high.peek()) / 2.0 : low.peek(); // Расширение до сложения защищает от переполнения int.
        }
        return result;
    }

    public static void main(String[] args) {
        check(new int[]{5, 1, 9, 2, 8, 3}, new double[]{5, 3, 5, 3.5, 5, 4});
        check(new int[]{}, new double[]{});
        check(new int[]{9}, new double[]{9});
        check(new int[]{7, 7, 7}, new double[]{7, 7, 7});
        check(new int[]{1, 2, 3, 4}, new double[]{1, 1.5, 2, 2.5});
        check(new int[]{4, 3, 2, 1}, new double[]{4, 3.5, 3, 2.5});
        check(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, new double[]{Integer.MAX_VALUE, Integer.MAX_VALUE});
        check(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}, new double[]{Integer.MIN_VALUE, -0.5});
    }
    static void check(int[] values, double[] want) {
        int[] before = values.clone(); double[] got = solve(values);
        if (!Arrays.equals(got, want) || !Arrays.equals(values, before)) throw new AssertionError("wrong median or changed input");
        System.out.println("OK " + Arrays.toString(got));
    }
}
