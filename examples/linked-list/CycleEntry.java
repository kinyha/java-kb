import java.util.*;

public class CycleEntry {
    static final class Node {
        final String id; // Имя объекта для трассы; совпадение value не означает один объект.
        final int value;
        Node next;
        Node(String id, int value) { this.id = id; this.value = value; }
    }

    static Node solve(Node head) {
        Node slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next; fast = fast.next.next;
            if (slow == fast) {
                Node finder = head;
                while (finder != slow) { finder = finder.next; slow = slow.next; }
                return finder; // Это сам узел входа, а не его value.
            }
        }
        return null;
    }
    public static void main(String[] args) {
        test(new int[0], -1); test(new int[]{1}, -1); test(new int[]{1}, 0);
        test(new int[]{1, 2}, 0); test(new int[]{1, 2}, 1);
        test(new int[]{10, 20, 30, 40, 50}, 2);
        test(new int[]{10, 20, 30, 40, 50}, 0);
        test(new int[]{7, 7, 7}, -1);
        test(new int[3_000], 2_700);
    }

    static void test(int[] values, int entryIndex) {
        Node[] input = nodes(values);
        Node want = entryIndex < 0 ? null : input[entryIndex];
        if (want != null) input[input.length - 1].next = want;
        Node[] links = Arrays.stream(input).map(n -> n.next).toArray(Node[]::new);
        Node got = solve(head(input));
        if (got != want) throw new AssertionError("got " + id(got) + ", want " + id(want));
        for (int i = 0; i < input.length; i++) if (input[i].next != links[i]) throw new AssertionError("input changed");
        System.out.println("OK entry=" + id(got));
    }

    static Node[] nodes(int... values) {
        Node[] result = new Node[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new Node(i < 26 ? String.valueOf((char) ('A' + i)) : "N" + i, values[i]);
            if (i > 0) result[i - 1].next = result[i];
        }
        return result;
    }

    static Node head(Node[] values) { return values.length == 0 ? null : values[0]; }
    static String id(Node n) { return n == null ? "null" : n.id; }

    static String show(Node n) {
        List<String> result = new ArrayList<>();
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (n != null) {
            if (!seen.add(n)) { result.add("cycle(" + n.id + ")"); break; }
            result.add(n.id + ":" + n.value);
            n = n.next;
        }
        return result.toString(); // Только печать использует O(n) память, не алгоритм.
    }
}
