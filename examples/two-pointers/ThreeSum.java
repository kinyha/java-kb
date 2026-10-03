import java.util.*;

public class ThreeSum {
    static List<List<Integer>> solve(int[] nums) {
        int[] a = nums.clone(); // Условие требует сохранить вход.
        Arrays.sort(a);
        var result = new ArrayList<List<Integer>>();
        for (int fixed = 0; fixed < a.length - 2; fixed++) {
            if (fixed > 0 && a[fixed] == a[fixed - 1]) continue; // Этот первый элемент уже разобран.
            if (a[fixed] > 0) break; // Дальше все три числа положительные.
            int left = fixed + 1, right = a.length - 1;
            while (left < right) {
                long sum = (long) a[fixed] + a[left] + a[right];
                if (sum < 0) left++;
                else if (sum > 0) right--;
                else {
                    result.add(List.of(a[fixed], a[left], a[right]));
                    int low = a[left], high = a[right];
                    while (left < right && a[left] == low) left++; // Пропускаем уже выданные значения.
                    while (left < right && a[right] == high) right--;
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        test(new int[]{-1, 0, 1, 2, -1, -4}, List.of(List.of(-1, -1, 2), List.of(-1, 0, 1)));
        test(new int[]{0, 0, 0, 0}, List.of(List.of(0, 0, 0)));
        test(new int[]{-2, -2, 0, 0, 2, 2}, List.of(List.of(-2, 0, 2)));
        test(new int[]{1, 2, 3}, List.of());
        test(new int[]{-5, -3, -1}, List.of());
        test(new int[0], List.of());
        test(new int[]{0, 0}, List.of());
        test(new int[]{Integer.MIN_VALUE, 1, Integer.MAX_VALUE}, List.of(List.of(Integer.MIN_VALUE, 1, Integer.MAX_VALUE)));
        test(new int[]{2, Integer.MAX_VALUE, Integer.MAX_VALUE}, List.of());
        test(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, 0}, List.of()); // Сумма int дала бы ложный ноль.
    }

    static void test(int[] nums, List<List<Integer>> want) {
        int[] before = nums.clone();
        check(solve(nums), want);
        if (!Arrays.equals(nums, before)) throw new AssertionError("input changed");
    }

    static void check(List<List<Integer>> got, List<List<Integer>> want) {
        if (!got.equals(want)) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
