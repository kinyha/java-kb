import java.util.Arrays;

public class PairDemo {
    record Search(int[] pair, int comparisons) {}

    static Search brute(int[] a, int target) {
        int checks = 0;
        for (int i = 0; i < a.length; i++) {
            for (int j = i + 1; j < a.length; j++) {
                checks++;
                if ((long) a[i] + a[j] == target) return new Search(new int[]{i, j}, checks);
            }
        }
        return new Search(new int[0], checks);
    }

    static int[] pair(int[] a, int target, boolean trace) {
        int left = 0, right = a.length - 1;
        while (left < right) { // Одна позиция не может участвовать дважды.
            long sum = (long) a[left] + a[right]; // Расширяем тип ДО сложения.
            if (trace) System.out.printf("left=%d right=%d sum=%d%n", left, right, sum);
            if (sum == target) return new int[]{left, right};
            if (sum < target) left++; // Даже самый большой партнёр слишком мал.
            else right--; // Даже самый маленький партнёр слишком велик.
        }
        return new int[0];
    }

    public static void main(String[] args) {
        System.out.println("== sorted pair ==");
        int[] a = {10, 20, 30, 40, 70, 90};
        Search slow = brute(a, 60);
        System.out.println("brute pair=" + Arrays.toString(slow.pair()) + " checks=" + slow.comparisons());
        System.out.println("two pointers=" + Arrays.toString(pair(a, 60, true)));
        System.out.println("input=" + Arrays.toString(a));

        System.out.println("== sorting is a precondition ==");
        int[] unsorted = {30, 20, 40};
        System.out.println("unsorted brute=" + Arrays.toString(brute(unsorted, 60).pair()));
        System.out.println("unsorted pointers=" + Arrays.toString(pair(unsorted, 60, false)));
        System.out.println("single 30, target 60=" + Arrays.toString(pair(new int[]{30}, 60, false)));
        System.out.println("two 30, target 60=" + Arrays.toString(pair(new int[]{30, 30}, 60, false)));
        System.out.println("negative values=" + Arrays.toString(pair(new int[]{-30, -10, 20, 40}, 10, false)));

        System.out.println("== overflow ==");
        int x = Integer.MAX_VALUE;
        System.out.println("int sum=" + (x + x));
        System.out.println("long sum=" + ((long) x + x));
        System.out.println("target -2, safe pair=" + Arrays.toString(pair(new int[]{x, x}, -2, false)));
    }
}
