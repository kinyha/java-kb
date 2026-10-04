import java.util.*;
public class RefundPair {
    record Pair(int i, int j) {}
    static Optional<Pair> solve(int[] amounts, int target) {
        Map<Integer, Integer> firstIndex = new HashMap<>();
        for (int j = 0; j < amounts.length; j++) {
            long need = (long) target - amounts[j]; // расширяем ДО вычитания
            if (need >= Integer.MIN_VALUE && need <= Integer.MAX_VALUE) {
                Integer i = firstIndex.get((int) need);
                if (i != null) return Optional.of(new Pair(i, j)); // i строго меньше j
            }
            firstIndex.putIfAbsent(amounts[j], j); // сохраняем самый ранний i
        }
        return Optional.empty();
    }
    static void check(String name, Object got, Object want) {
        if (!Objects.equals(got, want)) throw new AssertionError(name + ": " + got + " != " + want);
        System.out.println(name + " OK");
    }
    public static void main(String[] args) {
        check("empty", solve(new int[0], 14), Optional.empty());
        check("one position", solve(new int[]{7}, 14), Optional.empty());
        check("same amount", solve(new int[]{7, 7}, 14), Optional.of(new Pair(0, 1)));
        check("earliest j", solve(new int[]{1, 4, 6, 9}, 10), Optional.of(new Pair(1, 2)));
        check("earliest i", solve(new int[]{2, 2, 3}, 5), Optional.of(new Pair(0, 2)));
        check("signed", solve(new int[]{-5, 8, 3}, 3), Optional.of(new Pair(0, 1)));
        check("overflow", solve(new int[]{Integer.MAX_VALUE, 1}, Integer.MIN_VALUE), Optional.empty());
        check("min value", solve(new int[]{Integer.MIN_VALUE, 0}, Integer.MIN_VALUE), Optional.of(new Pair(0, 1)));
        int[] values = {3, 1, 7};
        int[] before = values.clone();
        solve(values, 10);
        check("input unchanged", Arrays.equals(values, before), true);
    }
}
