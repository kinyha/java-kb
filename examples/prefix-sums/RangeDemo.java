import java.util.*;

public class RangeDemo {
    record Result(long sum, int reads) {}

    static Result direct(int[] values, int left, int right) {
        long sum = 0;
        for (int i = left; i < right; i++) sum += values[i];
        return new Result(sum, right - left);
    }

    static long[] prefix(int[] values) {
        long[] p = new long[values.length + 1]; // P[0] — сумма пустого начала.
        for (int i = 0; i < values.length; i++) p[i + 1] = p[i] + values[i];
        return p; // P[i] — сумма ровно i первых элементов.
    }

    static long range(long[] p, int left, int right) {
        if (left < 0 || left > right || right >= p.length) throw new IllegalArgumentException("invalid range");
        return p[right] - p[left]; // Общие первые left элементов отменяются.
    }

    public static void main(String[] args) {
        System.out.println("== sums on boundaries ==");
        int[] payments = {10, -4, 7, 0, -3, 5};
        long[] p = prefix(payments);
        System.out.println("prefix=" + Arrays.toString(p));
        int reads = 0;
        int[][] queries = {{1, 5}, {2, 6}, {0, 6}, {3, 3}, {4, 5}};
        for (int[] q : queries) {
            Result slow = direct(payments, q[0], q[1]);
            long fast = range(p, q[0], q[1]);
            if (slow.sum() != fast) throw new AssertionError("range mismatch");
            reads += slow.reads();
            System.out.println("[" + q[0] + ", " + q[1] + ") direct=" + slow.sum() + " prefix=" + fast);
        }
        System.out.println("direct reads=" + reads + "; build reads=" + payments.length + "; prefix reads=" + queries.length * 2);
        System.out.println("empty=" + range(prefix(new int[0]), 0, 0));

        System.out.println("== widen before addition ==");
        int a = 2_000_000_000, b = 2_000_000_000;
        System.out.println("late cast=" + (long) (a + b)); // Сложение уже прошло в int.
        System.out.println("early cast=" + ((long) a + b));
        System.out.println("wide prefix=" + Arrays.toString(prefix(new int[]{a, b, -1})));
        try { Math.addExact(a, b); }
        catch (ArithmeticException e) { System.out.println("addExact=" + e.getClass().getSimpleName()); }

        System.out.println("== a prefix is a snapshot ==");
        payments[2] = 70;
        System.out.println("changed=" + Arrays.toString(payments));
        System.out.println("old [1, 4)=" + range(p, 1, 4));
        System.out.println("actual [1, 4)=" + direct(payments, 1, 4).sum());
        long[] rebuilt = prefix(payments);
        System.out.println("rebuilt=" + Arrays.toString(rebuilt));
        System.out.println("new [1, 4)=" + range(rebuilt, 1, 4));
    }
}
