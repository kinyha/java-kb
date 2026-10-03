import java.util.Arrays;

public class InPlaceDemo {
    static void reverse(int[] a) {
        for (int left = 0, right = a.length - 1; left < right; left++, right--) {
            int saved = a[left]; // Второе присваивание иначе потеряло бы прежнее a[left].
            a[left] = a[right];
            a[right] = saved;
        }
    }

    static int unique(int[] a, boolean trace) {
        int write = 0;
        for (int read = 0; read < a.length; read++) {
            if (write == 0 || a[read] != a[write - 1]) {
                a[write++] = a[read]; // Пишем в прочитанную часть: write <= read до записи.
                if (trace) System.out.println("keep read=" + read + " write=" + write + " prefix=" + Arrays.toString(Arrays.copyOf(a, write)));
            }
        }
        return write; // Меняется логическая длина. a.length остаётся прежней.
    }

    static int keepAtMost(int[] a, int limit) {
        if (limit < 0) throw new IllegalArgumentException("negative limit");
        if (limit == 0) return 0;
        int write = 0;
        for (int read = 0; read < a.length; read++) {
            if (write < limit || a[read] != a[write - limit]) {
                a[write++] = a[read]; // Сравниваем с уже собранным ответом, не с прошлым входом.
            }
        }
        return write;
    }

    public static void main(String[] args) {
        System.out.println("== reverse ==");
        int[] reverseInput = {10, 20, 30, 40, 50};
        int[] alias = reverseInput;
        reverse(reverseInput);
        System.out.println("array=" + Arrays.toString(reverseInput));
        System.out.println("alias=" + Arrays.toString(alias));
        reverse(reverseInput);
        System.out.println("twice=" + Arrays.toString(reverseInput));

        System.out.println("== compact ==");
        int[] amounts = {10, 10, 20, 20, 20, 30};
        int length = unique(amounts, true);
        System.out.println("length=" + length + " physical=" + amounts.length);
        System.out.println("array=" + Arrays.toString(amounts));
        System.out.println("answer=" + Arrays.toString(Arrays.copyOf(amounts, length)));

        System.out.println("== keep two copies ==");
        int[] repeated = {10, 10, 10, 20, 20, 20, 30};
        int kept = keepAtMost(repeated, 2);
        System.out.println("length=" + kept);
        System.out.println("answer=" + Arrays.toString(Arrays.copyOf(repeated, kept)));
        System.out.println("limit zero=" + keepAtMost(new int[]{10, 10}, 0));
    }
}
