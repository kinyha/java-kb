import java.util.*;

public class ReverseList {
    static final class Node {
        final String id; // Имя объекта для трассы; совпадение value не означает один объект.
        final int value;
        Node next;
        Node(String id, int value) { this.id = id; this.value = value; }
    }

    static Node solve(Node head) {
        Node prev = null, current = head;
        while (current != null) {
            Node next = current.next; // До перенаправления сохраняем необработанный остаток.
            current.next = prev;
            prev = current;
            current = next;
        }
        return prev;
    }
    public static void main(String[] args) {
        test(); test(1); test(1, 2); test(10, 20, 30); test(7, 7, 7);
        test(Integer.MIN_VALUE, 0, Integer.MAX_VALUE);
        test(new int[10_000]);
    }

    static void test(int... values) {
        Node[] input = nodes(values);
        Node got = solve(head(input)), current = got;
        for (int i = input.length - 1; i >= 0; i--) {
            if (current != input[i]) throw new AssertionError("wrong identity at " + i);
            current = current.next;
        }
        if (current != null) throw new AssertionError("extra nodes or cycle");
        System.out.println("OK length=" + values.length + " head=" + id(got));
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
