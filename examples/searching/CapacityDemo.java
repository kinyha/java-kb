import java.util.*;

public class CapacityDemo {
    static int daysNeeded(int[] weights, long capacity) {
        if (weights.length == 0) return 0;
        int used = 1;
        long load = 0;
        for (int weight : weights) {
            if (weight > capacity) return -1; // Даже отдельный пакет не помещается.
            if (load + weight > capacity) { used++; load = 0; }
            load += weight; // Порядок пакетов сохраняется, ничего не переставляем.
        }
        return used;
    }

    static long minimum(int[] weights, int days, boolean trace) {
        if (days < 1) throw new IllegalArgumentException("days must be positive");
        long left = 0, right = 0;
        for (int weight : weights) {
            if (weight < 0) throw new IllegalArgumentException("negative weight");
            left = Math.max(left, weight); // Меньше самого большого пакета нельзя.
            right += weight; // Всё помещается за один день, значит, эта граница допустима.
        }
        while (left < right) { // Минимальный допустимый ответ лежит в [left, right].
            long middle = left + (right - left) / 2;
            int used = daysNeeded(weights, middle);
            boolean fits = used >= 0 && used <= days;
            if (trace) System.out.println("[" + left + ", " + right + "] middle=" + middle + " days=" + used + " fits=" + fits);
            if (fits) right = middle; // Допустимый middle остаётся возможным минимумом.
            else left = middle + 1;
        }
        return left;
    }

    public static void main(String[] args) {
        System.out.println("== capacity predicate ==");
        int[] weights = {7, 2, 5, 10, 8};
        for (long capacity : new long[]{10, 15, 17, 18, 21, 32}) {
            int used = daysNeeded(weights, capacity);
            System.out.println("capacity=" + capacity + " days=" + used + " fits in 2=" + (used >= 0 && used <= 2));
        }

        System.out.println("== first feasible capacity ==");
        System.out.println("minimum=" + minimum(weights, 2, true));
        System.out.println("input=" + Arrays.toString(weights));

        System.out.println("== answer wider than int ==");
        int[] heavy = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        System.out.println("int total=" + (heavy[0] + heavy[1]));
        System.out.println("one day=" + minimum(heavy, 1, false));
        System.out.println("two days=" + minimum(heavy, 2, false));
        System.out.println("empty=" + minimum(new int[0], 2, false));
        System.out.println("zeros=" + minimum(new int[]{0, 0}, 1, false));
    }
}
