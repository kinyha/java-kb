import java.util.*;

public class MonotonicDemo {
    record Search(int[] next, int checks) {}
    record Scan(int[] next, int pushes, int pops) {}

    static Search brute(int[] values) {
        int[] next = new int[values.length];
        Arrays.fill(next, -1);
        int checks = 0;
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                checks++;
                if (values[j] > values[i]) { next[i] = j; break; }
            }
        }
        return new Search(next, checks);
    }

    static Scan nextGreater(int[] values, boolean trace) {
        int[] next = new int[values.length];
        Arrays.fill(next, -1); // -1 обозначает отсутствие ИНДЕКСА, не значения.
        var pending = new ArrayDeque<Integer>();
        int pushes = 0, pops = 0;
        for (int i = 0; i < values.length; i++) {
            while (!pending.isEmpty() && values[pending.peek()] < values[i]) {
                int earlier = pending.pop(); // Текущее значение завершает ожидание earlier.
                next[earlier] = i;
                pops++;
                if (trace) System.out.println("pop=" + earlier + " next=" + i + " pending(top first)=" + pending);
            }
            pending.push(i); // Сам i ждёт будущего большего значения.
            pushes++;
            if (trace) System.out.println("push=" + i + " pending(top first)=" + pending);
        }
        return new Scan(next, pushes, pops);
    }

    static int[] nextGreaterFromRight(int[] values) {
        int[] next = new int[values.length];
        var candidates = new ArrayDeque<Integer>();
        for (int i = values.length - 1; i >= 0; i--) {
            while (!candidates.isEmpty() && values[candidates.peek()] <= values[i]) {
                candidates.pop(); // Равный или меньший справа не годится как строго больший.
            }
            next[i] = candidates.isEmpty() ? -1 : candidates.peek();
            candidates.push(i); // Более близкое значение заменяет слабых дальних.
        }
        return next;
    }

    public static void main(String[] args) {
        System.out.println("== next greater on the right ==");
        int[] values = {70, 70, 65, 80};
        Search slow = brute(values);
        System.out.println("brute=" + Arrays.toString(slow.next()) + " checks=" + slow.checks());
        Scan scan = nextGreater(values, true);
        System.out.println("next indices=" + Arrays.toString(scan.next()));
        System.out.println("pushes=" + scan.pushes() + " pops=" + scan.pops());

        System.out.println("== equal values and negative values ==");
        int[] equal = {70, 70};
        System.out.println("forward equal=" + Arrays.toString(nextGreater(equal, false).next()));
        System.out.println("backward equal=" + Arrays.toString(nextGreaterFromRight(equal)));
        System.out.println("negative values=" + Arrays.toString(nextGreater(new int[]{-2, -1, -3}, false).next()));
        System.out.println("backward same input=" + Arrays.toString(nextGreaterFromRight(values)));

        System.out.println("== total work ==");
        int[] descending = {4, 3, 2, 1};
        Scan desc = nextGreater(descending, false);
        System.out.println("descending checks=" + brute(descending).checks() + " pushes=" + desc.pushes() + " pops=" + desc.pops());
        Scan asc = nextGreater(new int[]{1, 2, 3, 4}, false);
        System.out.println("ascending pushes=" + asc.pushes() + " pops=" + asc.pops());
    }
}
