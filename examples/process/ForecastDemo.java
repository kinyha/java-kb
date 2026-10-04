import java.util.*;

public class ForecastDemo {
    static void twoWeeks() {
        int[] history = {4, 6, 5, 7, 3, 8, 6, 5}; // учебная история карточек в неделю
        int remaining = 10, success = 0, cases = 0;
        Map<Integer, Integer> capacityCounts = new TreeMap<>();
        for (int first : history) {
            for (int second : history) {           // независимый выбор с возвращением
                int capacity = first + second;
                capacityCounts.merge(capacity, 1, Integer::sum);
                if (capacity >= remaining) success++;
                cases++;
            }
        }
        System.out.println("== two weeks are a forecast, not a promise ==");
        System.out.println("history=" + Arrays.toString(history));
        System.out.println("two-week capacities=" + capacityCounts);
        System.out.println("completed10=" + success + "/" + cases);
        System.out.printf(Locale.ROOT, "model share=%.2f%%%n", 100.0 * success / cases);
        System.out.println("lowest=" + Collections.min(capacityCounts.keySet())
                + " highest=" + Collections.max(capacityCounts.keySet()));
    }

    public static void main(String[] args) {
        twoWeeks();
    }
}
