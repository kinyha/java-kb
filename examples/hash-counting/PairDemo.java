import java.util.*;

public class PairDemo {
    record Brute(int[] pair, int comparisons) {}

    static Brute brute(int[] nums, int target) {
        int comparisons = 0;
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) { // Разные позиции, каждую пару один раз.
                comparisons++;
                if ((long) nums[i] + nums[j] == target) return new Brute(new int[]{i, j}, comparisons);
            }
        }
        return new Brute(new int[0], comparisons);
    }

    static int[] find(int[] nums, int target, boolean trace) {
        var seen = new LinkedHashMap<Long, Integer>(); // Порядок нужен только для читаемой печати.
        for (int j = 0; j < nums.length; j++) {
            long need = (long) target - nums[j]; // Сначала расширяем тип, потом вычитаем.
            Integer i = seen.get(need); // Таблица содержит только позиции < j.
            if (trace) System.out.println("j=" + j + " x=" + nums[j] + " need=" + need + " before=" + seen + " found=" + i);
            if (i != null) return new int[]{i, j};
            seen.putIfAbsent((long) nums[j], j); // Сохраняем самый ранний индекс для равного значения.
        }
        return new int[0];
    }

    static int[] wrongInsertFirst(int[] nums, int target) {
        var seen = new HashMap<Long, Integer>();
        for (int j = 0; j < nums.length; j++) {
            seen.put((long) nums[j], j); // Ошибка: j уже доступен как «предыдущий» индекс.
            Integer i = seen.get((long) target - nums[j]);
            if (i != null) return new int[]{i, j};
        }
        return new int[0];
    }

    public static void main(String[] args) {
        int[] amounts = {40, 20, 90, 30};
        System.out.println("== repeated search ==");
        Brute result = brute(amounts, 50);
        System.out.println("brute pair=" + Arrays.toString(result.pair()) + " comparisons=" + result.comparisons());
        System.out.println("== prior prefix ==");
        System.out.println("hash pair=" + Arrays.toString(find(amounts, 50, true)));
        System.out.println("== order and range ==");
        System.out.println("insert first [5]=" + Arrays.toString(wrongInsertFirst(new int[]{5}, 10)));
        System.out.println("query first [5]=" + Arrays.toString(find(new int[]{5}, 10, false)));
        System.out.println("query first [5,5]=" + Arrays.toString(find(new int[]{5, 5}, 10, false)));
        int max = Integer.MAX_VALUE;
        System.out.println("int sum=" + (max + max) + " long sum=" + ((long) max + max));
        System.out.println("pair MAX,MAX target=-2=" + Arrays.toString(find(new int[]{max, max}, -2, false)));
        System.out.println("earliest index=" + Arrays.toString(find(new int[]{4, 4, 6}, 10, false)));
    }
}
