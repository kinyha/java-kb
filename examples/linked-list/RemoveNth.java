import java.util.*;

public class RemoveNth {
    static final class Node {
        final String id; // Имя объекта для трассы; совпадение value не означает один объект.
        final int value;
        Node next;
        Node(String id, int value) { this.id = id; this.value = value; }
    }

    static Node solve(Node head, int n) {
        Node dummy = new Node("DUMMY", 0); dummy.next = head;
        Node slow = dummy, fast = dummy;
        for (int i = 0; i <= n; i++) fast = fast.next; // n+1 переходов, n допустимо по условию.
        while (fast != null) { slow = slow.next; fast = fast.next; }
        slow.next = slow.next.next; // slow — предшественник, включая dummy для удаления головы.
        return dummy.next;
    }
    public static void main(String[] args) {
        test(new int[]{1}, 1); test(new int[]{1, 2}, 2); test(new int[]{1, 2}, 1);
        test(new int[]{10, 20, 30, 40, 50}, 2);
        test(new int[]{10, 20, 30, 40, 50}, 5);
        test(new int[]{10, 20, 30, 40, 50}, 1);
        test(new int[]{7, 7, 7}, 2);
        test(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}, 1);
    }

    static void test(int[] values, int n) {
        Node[] input = nodes(values);
        int removed = values.length - n;
        Node got = solve(input[0], n), current = got;
        for (int i = 0; i < input.length; i++) {
            if (i == removed) continue;
            if (current != input[i]) throw new AssertionError("wrong identity at " + i);
            current = current.next;
        }
        if (current != null) throw new AssertionError("extra nodes or cycle");
        System.out.println("OK " + show(got));
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
