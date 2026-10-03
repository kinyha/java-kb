import java.util.*;

public class ShipCapacity {
    static long solve(int[] weights, int days) {
        long left = 0, right = 0;
        for (int weight : weights) {
            left = Math.max(left, weight);
            right += weight; // Верхняя граница допустима: всё уйдёт за один день.
        }
        while (left < right) {
            long middle = left + (right - left) / 2;
            if (canShip(weights, days, middle)) right = middle; // Успешная середина ещё может быть минимумом.
            else left = middle + 1;
        }
        return left;
    }

    static boolean canShip(int[] weights, int days, long capacity) {
        int used = 1;
        long load = 0;
        for (int weight : weights) {
            if (weight > capacity) return false;
            if (load + weight > capacity) {
                used++;
                load = 0; // Следующий пакет начинает новый день, порядок сохраняется.
                if (used > days) return false;
            }
            load += weight;
        }
        return true;
    }

    public static void main(String[] args) {
        test(new int[]{7, 2, 5, 10, 8}, 2, 18);
        test(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 5, 15);
        test(new int[]{3, 2, 2, 4, 1, 4}, 3, 6);
        test(new int[]{1, 2, 3, 1, 1}, 4, 3);
        test(new int[]{5, 5, 5}, 5, 5);
        test(new int[0], 2, 0);
        test(new int[]{0, 0}, 1, 0);
        test(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 1, 4294967294L);
        test(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE}, 2, Integer.MAX_VALUE);
        test(new int[]{1, Integer.MAX_VALUE}, 1, 2147483648L);
    }

    static void test(int[] input, int days, long want) {
        int[] before = input.clone();
        check(solve(input, days), want);
        if (!Arrays.equals(input, before)) throw new AssertionError("input changed");
    }

    static void check(long got, long want) {
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
