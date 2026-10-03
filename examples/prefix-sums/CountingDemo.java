import java.util.*;

public class CountingDemo {
    static long brute(int[] values, long target) {
        long answer = 0;
        for (int left = 0; left < values.length; left++) {
            long sum = 0;
            for (int right = left; right < values.length; right++) {
                sum += values[right]; // Не пересчитываем отрезок целиком: уже O(n²), не O(n³).
                if (sum == target) answer++;
            }
        }
        return answer;
    }

    static long count(int[] values, long target, boolean trace) {
        Map<Long, Long> frequency = new HashMap<>();
        frequency.put(0L, 1L); // Одна граница до первого элемента: P[0]=0.
        long sum = 0, answer = 0;
        int right = 0;
        for (int value : values) {
            right++;
            sum += value;
            long need = sum - target;
            long ways = frequency.getOrDefault(need, 0L); // Только уже прошедшие границы left < right.
            answer += ways;
            if (trace) System.out.println("r=" + right + " prefix=" + sum + " need=" + need + " ways=" + ways + " total=" + answer);
            frequency.merge(sum, 1L, Long::sum); // Текущая граница станет прошлой для следующего шага.
        }
        return answer;
    }

    static long wrongCount(int[] values, long target, boolean seed, boolean writeFirst) {
        Map<Long, Long> frequency = new HashMap<>();
        if (seed) frequency.put(0L, 1L);
        long sum = 0, answer = 0;
        for (int value : values) {
            sum += value;
            if (writeFirst) frequency.merge(sum, 1L, Long::sum); // Ошибка: собственная граница уже в карте.
            answer += frequency.getOrDefault(sum - target, 0L);
            if (!writeFirst) frequency.merge(sum, 1L, Long::sum);
        }
        return answer;
    }

    static long wrongSet(int[] values, long target) {
        Set<Long> seen = new HashSet<>();
        seen.add(0L);
        long sum = 0, answer = 0;
        for (int value : values) {
            sum += value;
            if (seen.contains(sum - target)) answer++; // Ошибка: теряется число разных границ с одной суммой.
            seen.add(sum);
        }
        return answer;
    }

    static long wrongWindow(int[] values, long target) {
        long sum = 0, answer = 0;
        int left = 0;
        for (int right = 0; right < values.length; right++) {
            sum += values[right];
            while (left <= right && sum > target) sum -= values[left++];
            if (left <= right && sum == target) answer++;
        }
        return answer;
    }

    public static void main(String[] args) {
        System.out.println("== previous equal prefixes ==");
        int[] refunds = {2, -2, 2, -2};
        System.out.println("brute=" + brute(refunds, 0));
        System.out.println("map=" + count(refunds, 0, true));

        System.out.println("== multiplicity and number of ranges ==");
        int[] zeros = {0, 0, 0};
        System.out.println("frequency=" + count(zeros, 0, false));
        System.out.println("set=" + wrongSet(zeros, 0));
        int[] many = new int[100_000];
        long total = count(many, 0, false);
        System.out.println("100000 zeros=" + total + "; cast to int=" + (int) total);

        System.out.println("== initialization and ordering ==");
        System.out.println("[3], target 3 correct=" + count(new int[]{3}, 3, false));
        System.out.println("[3], target 3 no seed=" + wrongCount(new int[]{3}, 3, false, false));
        System.out.println("[0], target 0 correct=" + count(new int[]{0}, 0, false));
        System.out.println("[0], target 0 write first=" + wrongCount(new int[]{0}, 0, true, true));
        System.out.println("empty=" + count(new int[0], 0, false));

        System.out.println("== a refund breaks the window ==");
        int[] mixed = {4, -1, 1};
        System.out.println("window=" + wrongWindow(mixed, 3));
        System.out.println("brute=" + brute(mixed, 3));
    }
}
