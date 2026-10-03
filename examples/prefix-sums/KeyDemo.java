import java.util.*;

public class KeyDemo {
    static int balanced(int[] values, boolean overwrite, boolean trace) {
        Map<Integer, Integer> first = new HashMap<>();
        first.put(0, 0); // Нулевой баланс перед элементами, позиция границы 0.
        int balance = 0, best = 0;
        for (int right = 1; right <= values.length; right++) {
            balance += values[right - 1] == 0 ? -1 : 1;
            Integer left = first.get(balance);
            if (left != null) best = Math.max(best, right - left);
            if (trace) System.out.println("r=" + right + " balance=" + balance + " first=" + left + " best=" + best);
            if (overwrite) first.put(balance, right); // Ошибка для максимальной длины: заменяем раннюю границу.
            else first.putIfAbsent(balance, right); // Чем раньше начало, тем длиннее отрезок.
        }
        return best;
    }

    static long divisible(int[] values, int divisor, boolean normalize) {
        Map<Integer, Long> frequency = new HashMap<>();
        frequency.put(0, 1L);
        long sum = 0, answer = 0;
        for (int value : values) {
            sum += value;
            int remainder = normalize ? (int) Math.floorMod(sum, (long) divisor) : (int) (sum % divisor);
            answer += frequency.getOrDefault(remainder, 0L); // Разность префиксов кратна divisor.
            frequency.merge(remainder, 1L, Long::sum);
        }
        return answer;
    }

    public static void main(String[] args) {
        System.out.println("== earliest boundary gives longest range ==");
        int[] flags = {0, 1, 0, 1, 1, 0};
        System.out.println("earliest=" + balanced(flags, false, true));
        System.out.println("overwrite=" + balanced(flags, true, false));

        System.out.println("== normalize negative remainders ==");
        System.out.println("-1 % 5=" + (-1 % 5));
        System.out.println("floorMod(-1, 5)=" + Math.floorMod(-1, 5));
        int[] values = {-1, 5};
        System.out.println("raw remainder count=" + divisible(values, 5, false));
        System.out.println("normalized count=" + divisible(values, 5, true));
        System.out.println("classic=" + divisible(new int[]{4, 5, 0, -2, -3, 1}, 5, true));
    }
}
