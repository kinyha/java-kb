import java.util.*;

public class MergeDemo {
    static boolean overlaps(int[] a, int[] b, boolean closed) {
        int left = Math.max(a[0], b[0]), right = Math.min(a[1], b[1]);
        return closed ? left <= right : left < right;
    }
    static int[][] merge(int[][] input, boolean trace) {
        int[][] sorted = input.clone(); // Копируем порядок ссылок; строки ниже только читаем.
        Arrays.sort(sorted, Comparator.comparingInt(a -> a[0]));
        List<int[]> result = new ArrayList<>();
        for (int[] next : sorted) {
            if (result.isEmpty() || next[0] > result.getLast()[1]) {
                result.add(next.clone()); // Новый компонент; ответ не разделяет строки со входом.
            } else {
                int[] current = result.getLast();
                current[1] = Math.max(current[1], next[1]); // Вложенный интервал не сокращает конец.
            }
            if (trace) System.out.println("next=" + Arrays.toString(next) + " result=" + Arrays.deepToString(result.toArray()));
        }
        return result.toArray(int[][]::new);
    }
    static int[][] wrongMerge(int[][] input) {
        int[][] sorted = input.clone(); Arrays.sort(sorted, Comparator.comparingInt(a -> a[0]));
        List<int[]> result = new ArrayList<>();
        for (int[] next : sorted) {
            if (result.isEmpty() || next[0] > result.getLast()[1]) result.add(next.clone());
            else result.getLast()[1] = next[1]; // Ошибка: вложение отодвигает конец влево.
        }
        return result.toArray(int[][]::new);
    }

    public static void main(String[] args) {
        System.out.println("== equal endpoint changes meaning ==");
        int[] a = {1, 3}, b = {3, 5};
        System.out.println("closed overlap=" + overlaps(a, b, true));
        System.out.println("half-open overlap=" + overlaps(a, b, false));
        System.out.println("point closed=" + overlaps(new int[]{3, 3}, a, true));
        System.out.println("empty half-open=" + overlaps(new int[]{3, 3}, a, false));

        System.out.println("== nested interval must not shrink end ==");
        int[][] input = {{8, 10}, {2, 3}, {1, 7}, {6, 9}};
        int[][] merged = merge(input, true);
        System.out.println("merged=" + Arrays.deepToString(merged));
        System.out.println("wrong=" + Arrays.deepToString(wrongMerge(input)));
        System.out.println("input=" + Arrays.deepToString(input));

        System.out.println("== copying outer array does not copy rows ==");
        int[][] source = {{1, 4}, {3, 6}};
        int[][] shallow = source.clone(); shallow[0][1] = 99;
        System.out.println("shallow source=" + Arrays.deepToString(source));
        int[][] fresh = {{1, 4}, {3, 6}};
        int[][] answer = merge(fresh, false); answer[0][1] = 77;
        System.out.println("answer changed=" + Arrays.deepToString(answer));
        System.out.println("fresh input=" + Arrays.deepToString(fresh));

        System.out.println("== subtraction can break ordering and length ==");
        int low = Integer.MIN_VALUE, high = Integer.MAX_VALUE;
        System.out.println("subtract=" + (low - high) + " compare=" + Integer.compare(low, high));
        int[][] extreme = {{0, 0}, {low, low}, {high, high}};
        System.out.println("safe merge=" + Arrays.deepToString(merge(extreme, false)));
        System.out.println("int length=" + (high - low) + " long length=" + ((long) high - low));
    }
}
