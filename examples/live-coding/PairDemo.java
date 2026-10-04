import java.util.*;
public class PairDemo {
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
    static Optional<Pair> wrongSelf(int[] amounts, int target) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int j = 0; j < amounts.length; j++) {
            seen.putIfAbsent(amounts[j], j); // записали текущий индекс до поиска
            long need = (long) target - amounts[j];
            if (need >= Integer.MIN_VALUE && need <= Integer.MAX_VALUE) {
                Integer i = seen.get((int) need);
                if (i != null) return Optional.of(new Pair(i, j));
            }
        }
        return Optional.empty();
    }
    static Optional<Pair> wrongInt(int[] amounts, int target) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int j = 0; j < amounts.length; j++) {
            int need = target - amounts[j]; // переполнение создаёт ложное дополнение
            Integer i = seen.get(need);
            if (i != null) return Optional.of(new Pair(i, j));
            seen.putIfAbsent(amounts[j], j);
        }
        return Optional.empty();
    }
    public static void main(String[] args) {
        System.out.println("== a payment cannot pair with itself ==");
        System.out.println("write first=" + wrongSelf(new int[]{7}, 14));
        System.out.println("read first=" + solve(new int[]{7}, 14));
        System.out.println("two positions=" + solve(new int[]{7, 7}, 14));
        System.out.println("== widen before subtracting ==");
        int[] large = {Integer.MAX_VALUE, 1};
        System.out.println("int complement=" + wrongInt(large, Integer.MIN_VALUE));
        System.out.println("long complement=" + solve(large, Integer.MIN_VALUE));
        System.out.println("actual sum=" + ((long) large[0] + large[1]));
        System.out.println("late cast=" + (long) (Integer.MIN_VALUE - 1));
        System.out.println("early cast=" + ((long) Integer.MIN_VALUE - 1));
    }
}
