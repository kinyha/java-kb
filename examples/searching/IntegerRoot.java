public class IntegerRoot {
    static int solve(int value) {
        long left = 0, right = (long) value + 1; // Граница может не поместиться в int.
        while (left < right) {
            long middle = left + (right - left) / 2;
            if (middle * middle > value) right = middle; // Первый недопустимый квадрат.
            else left = middle + 1;
        }
        return (int) (left - 1); // Предыдущая позиция — последний допустимый корень.
    }

    public static void main(String[] args) {
        test(0, 0);
        test(1, 1);
        test(2, 1);
        test(4, 2);
        test(8, 2);
        test(9, 3);
        test(20, 4);
        test(2_147_395_600, 46_340);
        test(Integer.MAX_VALUE, 46_340);
    }

    static void test(int value, int want) {
        int got = solve(value);
        check(got, want);
        if ((long) got * got > value || (long) (got + 1) * (got + 1) <= value) throw new AssertionError("not floor root");
    }

    static void check(int got, int want) {
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
