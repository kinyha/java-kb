import java.util.*;

public class PairDemo {
    static int[][] insert(int[][] input, int[] added, boolean trace) {
        List<int[]> result = new ArrayList<>();
        int i = 0, left = added[0], right = added[1];
        while (i < input.length && input[i][1] < left) {
            result.add(input[i++].clone()); // Левый префикс уже не пересечётся с добавленным.
        }
        while (i < input.length && input[i][0] <= right) {
            left = Math.min(left, input[i][0]);
            right = Math.max(right, input[i][1]); // Расширенный конец влияет на следующий шаг.
            if (trace) System.out.println("absorb=" + Arrays.toString(input[i]) + " pending=[" + left + ", " + right + "]");
            i++;
        }
        result.add(new int[]{left, right});
        while (i < input.length) result.add(input[i++].clone()); // Правый суффикс не затронут.
        return result.toArray(int[][]::new);
    }
    static int[][] intersect(int[][] a, int[][] b, boolean trace) {
        List<int[]> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.length && j < b.length) {
            int left = Math.max(a[i][0], b[j][0]);
            int right = Math.min(a[i][1], b[j][1]);
            if (left <= right) result.add(new int[]{left, right}); // Для закрытых границ точка тоже ответ.
            if (trace) System.out.println("i=" + i + " j=" + j + " candidate=[" + left + ", " + right + "] add=" + (left <= right));
            if (a[i][1] < b[j][1]) i++; // Раньше закончившийся уже не встретит следующий из другого списка.
            else if (a[i][1] > b[j][1]) j++;
            else { i++; j++; } // Оба конца исчерпаны.
        }
        return result.toArray(int[][]::new);
    }

    public static void main(String[] args) {
        System.out.println("== insertion expands its own boundary ==");
        int[][] input = {{1, 2}, {3, 5}, {6, 7}, {8, 10}, {12, 16}};
        int[] added = {4, 8};
        System.out.println("inserted=" + Arrays.deepToString(insert(input, added, true)));
        System.out.println("input=" + Arrays.deepToString(input) + " added=" + Arrays.toString(added));

        System.out.println("== advance the smaller end ==");
        int[][] a = {{0, 2}, {5, 10}, {13, 16}};
        int[][] b = {{1, 5}, {8, 12}, {15, 18}};
        System.out.println("intersections=" + Arrays.deepToString(intersect(a, b, true)));
        System.out.println("point=" + Arrays.deepToString(intersect(new int[][]{{1, 3}}, new int[][]{{3, 5}}, false)));
        System.out.println("empty=" + Arrays.deepToString(intersect(a, new int[0][], false)));

        System.out.println("== internal overlap breaks the discard proof ==");
        int[][] invalid = {{1, 5}, {2, 6}};
        System.out.println("invalid result=" + Arrays.deepToString(intersect(invalid, new int[][]{{3, 4}}, false)));
    }
}
