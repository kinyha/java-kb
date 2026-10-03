import java.util.*;

public class SortedDemo {
    record Search(int index, int checks) {}

    static Search linear(int[] values, int target) {
        int checks = 0;
        for (int i = 0; i < values.length; i++) {
            checks++;
            if (values[i] == target) return new Search(i, checks);
        }
        return new Search(-1, checks);
    }

    static Search exact(int[] values, int target, boolean trace) {
        int left = 0, right = values.length - 1, checks = 0;
        while (left <= right) { // Оба края входят в область ещё возможного ответа.
            int middle = left + (right - left) / 2;
            checks++;
            if (trace) System.out.println("exact [" + left + ", " + right + "] middle=" + middle + " value=" + values[middle]);
            if (values[middle] == target) return new Search(middle, checks);
            if (values[middle] < target) left = middle + 1; // middle уже проверен, исключаем его тоже.
            else right = middle - 1;
        }
        return new Search(-1, checks);
    }

    static int firstAtLeast(int[] values, int target, boolean trace) {
        int left = 0, right = values.length;
        while (left < right) { // Неисследованные элементы находятся в [left, right).
            int middle = left + (right - left) / 2;
            if (trace) System.out.println("lower [" + left + ", " + right + ") middle=" + middle + " value=" + values[middle]);
            if (values[middle] < target) left = middle + 1;
            else right = middle; // Равный элемент может быть первым: сохраняем его как границу.
        }
        return left; // Может оказаться n — это позиция, а не читаемый элемент.
    }

    static int firstGreater(int[] values, int target) {
        int left = 0, right = values.length;
        while (left < right) {
            int middle = left + (right - left) / 2;
            if (values[middle] <= target) left = middle + 1; // Проходим всю серию равных.
            else right = middle;
        }
        return left;
    }

    public static void main(String[] args) {
        System.out.println("== exact value ==");
        int[] amounts = {10, 20, 30, 30, 30, 50, 80};
        Search slow = linear(amounts, 80);
        System.out.println("linear index=" + slow.index() + " checks=" + slow.checks());
        Search found = exact(amounts, 80, true);
        System.out.println("binary index=" + found.index() + " checks=" + found.checks());
        System.out.println("absent=" + exact(amounts, 25, false).index());

        System.out.println("== boundaries, not just equality ==");
        int lower = firstAtLeast(amounts, 30, true);
        int upper = firstGreater(amounts, 30);
        System.out.println("equal range=[" + lower + ", " + upper + ") count=" + (upper - lower));
        int insert = firstAtLeast(amounts, 25, false);
        System.out.println("target 25 lower=" + insert + " present=" + (insert < amounts.length && amounts[insert] == 25));
        System.out.println("target 5 lower=" + firstAtLeast(amounts, 5, false));
        System.out.println("target 90 lower=" + firstAtLeast(amounts, 90, false));
        System.out.println("empty lower=" + firstAtLeast(new int[0], 30, false));
        int[] biggest = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        System.out.println("MAX upper=" + firstGreater(biggest, Integer.MAX_VALUE));
        System.out.println("MAX lower(target + 1)=" + firstAtLeast(biggest, Integer.MAX_VALUE + 1, false));

        System.out.println("== Arrays.binarySearch contract ==");
        int duplicate = Arrays.binarySearch(amounts, 30);
        System.out.println("JDK duplicate index=" + duplicate + " first=" + lower);
        int encoded = Arrays.binarySearch(amounts, 25);
        System.out.println("target 25 encoded=" + encoded + " insertion=" + (-encoded - 1));
        int tail = Arrays.binarySearch(amounts, 90);
        System.out.println("target 90 encoded=" + tail + " insertion=" + (-tail - 1));
        int range = Arrays.binarySearch(amounts, 2, 5, 20); // Ищем только среди трёх тридцаток.
        System.out.println("range [2, 5) encoded=" + range + " absolute insertion=" + (-range - 1));

        System.out.println("== middle can overflow without an array ==");
        int left = 1_600_000_000, right = 2_000_000_000;
        System.out.println("naive middle=" + ((left + right) / 2));
        System.out.println("safe middle=" + (left + (right - left) / 2));
    }
}
