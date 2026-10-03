import java.util.Arrays;

public class MergeDemo {
    record Payment(int amount, String id) {}

    static Payment[] merge(Payment[] a, Payment[] b) {
        Payment[] result = new Payment[a.length + b.length];
        int i = 0, j = 0, k = 0;
        while (i < a.length && j < b.length) {
            // При равенстве платёж из левой выгрузки идёт первым: слияние устойчиво.
            result[k++] = a[i].amount() <= b[j].amount() ? a[i++] : b[j++];
        }
        while (i < a.length) result[k++] = a[i++];
        while (j < b.length) result[k++] = b[j++];
        return result;
    }

    static void mergeInto(int[] a, int m, int[] b, int n, boolean trace) {
        // Первые m и n элементов отсортированы; a != b, a.length >= m + n.
        int i = m - 1, j = n - 1, k = m + n - 1;
        while (j >= 0) {
            if (i >= 0 && a[i] > b[j]) a[k--] = a[i--];
            else a[k--] = b[j--]; // При равенстве правый идёт в конец: левый останется раньше.
            if (trace) System.out.println("i=" + i + " j=" + j + " k=" + k + " array=" + Arrays.toString(a));
        }
        // Остаток a уже лежит на своих местах. Переписывать его не нужно.
    }

    public static void main(String[] args) {
        System.out.println("== forward, separate result ==");
        Payment[] first = {new Payment(10, "A1"), new Payment(30, "A2"), new Payment(30, "A3")};
        Payment[] second = {new Payment(20, "B1"), new Payment(30, "B2")};
        System.out.println("ids=" + Arrays.toString(Arrays.stream(merge(first, second)).map(Payment::id).toArray()));

        System.out.println("== backward, same array ==");
        int[] a = {10, 30, 50, 0, 0, 0};
        int[] b = {20, 40, 60};
        mergeInto(a, 3, b, 3, true);
        System.out.println("b=" + Arrays.toString(b));
        int[] withZero = {-3, 0, 4, 0, 0};
        mergeInto(withZero, 3, new int[]{0, 2}, 2, false);
        System.out.println("zero is data=" + Arrays.toString(withZero));

        System.out.println("== unsafe forward write ==");
        int[] unread = {2, 4, 6, 0, 0, 0};
        int[] incoming = {1, 3, 5};
        int i = 0, j = 0, k = 0;
        unread[k++] = incoming[j++]; // 1 идёт первым, но затёр ещё не прочитанное 2.
        System.out.println("unread a[i]=" + unread[i]);
        System.out.println("array=" + Arrays.toString(unread));
    }
}
