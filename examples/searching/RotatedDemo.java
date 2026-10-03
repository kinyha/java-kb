import java.util.*;

public class RotatedDemo {
    record Search(int index, int checks) {}

    static int distinct(int[] values, int target, boolean trace) {
        int left = 0, right = values.length - 1;
        while (left <= right) {
            int middle = left + (right - left) / 2;
            if (trace) System.out.println("[" + left + ", " + right + "] middle=" + middle + " value=" + values[middle]);
            if (values[middle] == target) return middle;
            if (values[left] <= values[middle]) { // Левая половина отсортирована.
                if (values[left] <= target && target < values[middle]) right = middle - 1;
                else left = middle + 1;
            } else { // Отсортирована правая половина.
                if (values[middle] < target && target <= values[right]) left = middle + 1;
                else right = middle - 1;
            }
        }
        return -1;
    }

    static Search withDuplicates(int[] values, int target) {
        int left = 0, right = values.length - 1, checks = 0;
        while (left <= right) {
            int middle = left + (right - left) / 2;
            checks++;
            if (values[middle] == target) return new Search(middle, checks);
            if (values[left] == values[middle] && values[middle] == values[right]) {
                left++; right--; // Края точно не target, но сторону разрыва различить нельзя.
            } else if (values[left] <= values[middle]) {
                if (values[left] <= target && target < values[middle]) right = middle - 1;
                else left = middle + 1;
            } else {
                if (values[middle] < target && target <= values[right]) left = middle + 1;
                else right = middle - 1;
            }
        }
        return new Search(-1, checks);
    }

    public static void main(String[] args) {
        System.out.println("== rotated distinct values ==");
        int[] values = {40, 50, 60, 70, 10, 20, 30};
        System.out.println("target 10 index=" + distinct(values, 10, true));
        System.out.println("target 99 index=" + distinct(values, 99, false));
        System.out.println("empty=" + distinct(new int[0], 10, false));

        System.out.println("== duplicates hide the sorted half ==");
        int[] repeated = {1, 0, 1, 1, 1};
        System.out.println("distinct-only on duplicates=" + distinct(repeated, 0, false));
        Search correct = withDuplicates(repeated, 0);
        System.out.println("duplicate-aware index=" + correct.index());
        Search same = withDuplicates(new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1}, 0);
        System.out.println("all equal, absent index=" + same.index() + " checks=" + same.checks());
    }
}
