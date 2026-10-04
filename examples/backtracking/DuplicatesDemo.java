import java.util.*;

public class DuplicatesDemo {
    static List<List<Integer>> subsets(int[] input, int mode) {
        int[] values = input.clone();
        Arrays.sort(values);
        List<List<Integer>> answers = new ArrayList<>();
        walk(values, 0, new ArrayList<>(), answers, mode);
        return answers;
    }
    static void walk(int[] values, int start, List<Integer> path,
                     List<List<Integer>> answers, int mode) {
        answers.add(List.copyOf(path));
        for (int i = start; i < values.length; i++) {
            boolean repeated = i > 0 && values[i] == values[i - 1];
            if (mode == 1 && i > start && repeated) continue; // Пропускаем равную альтернативу на этом уровне.
            if (mode == 2 && repeated) continue;             // Ошибка: запрещает вторую 1 и на следующем уровне.
            path.add(values[i]);
            walk(values, i + 1, path, answers, mode);
            path.remove(path.size() - 1);
        }
    }
    public static void main(String[] args) {
        System.out.println("== equal values at different positions ==");
        int[] input = {1,1,2};
        System.out.println("positional=" + subsets(input, 0));
        System.out.println("unique=" + subsets(input, 1));
        System.out.println("global skip=" + subsets(input, 2));

        System.out.println("== a second equal value is legal deeper ==");
        System.out.println("two equal=" + subsets(new int[]{1,1}, 1));
        System.out.println("all equal=" + subsets(new int[]{2,2,2}, 1));
        System.out.println("input=" + Arrays.toString(input));
    }
}
