import java.util.*;

public class MergeLists {
    static final class Node {
        final String id; // Имя объекта для трассы; совпадение value не означает один объект.
        final int value;
        Node next;
        Node(String id, int value) { this.id = id; this.value = value; }
    }

    static Node solve(Node a, Node b) {
        Node dummy = new Node("DUMMY", 0), tail = dummy;
        while (a != null && b != null) {
            if (a.value <= b.value) { tail.next = a; a = a.next; }
            else { tail.next = b; b = b.next; }
            tail = tail.next; // Готовый префикс растёт на один исходный узел.
        }
        tail.next = a != null ? a : b;
        return dummy.next;
    }
    public static void main(String[] args) {
        test(new int[0], new int[0]);
        test(new int[0], new int[]{1}, "B0");
        test(new int[]{1}, new int[0], "A0");
        test(new int[]{1, 3, 5}, new int[]{2, 3, 6}, "A0", "B0", "A1", "B1", "A2", "B2");
        test(new int[]{2, 2}, new int[]{2, 2}, "A0", "A1", "B0", "B1");
        test(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}, new int[]{0}, "A0", "B0", "A1");
        test(new int[]{9}, new int[]{1, 2, 3, 4}, "B0", "B1", "B2", "B3", "A0");
        test(new int[]{-5, -1}, new int[]{-5, 0}, "A0", "B0", "A1", "B1");
    }

    static void test(int[] first, int[] second, String... order) {
        Map<String, Node> original = new HashMap<>();
        Node a = named("A", first, original), b = named("B", second, original);
        if (order.length != original.size()) throw new AssertionError("incomplete expected order");
        Node got = solve(a, b), current = got;
        for (String label : order) {
            if (current != original.get(label)) throw new AssertionError("wrong identity: expected " + label);
            current = current.next;
        }
        if (current != null) throw new AssertionError("extra nodes or cycle");
        System.out.println("OK " + show(got));
    }

    static Node named(String prefix, int[] values, Map<String, Node> original) {
        Node result = null, tail = null;
        for (int i = 0; i < values.length; i++) {
            Node n = new Node(prefix + i, values[i]); original.put(n.id, n);
            if (tail == null) result = n; else tail.next = n;
            tail = n;
        }
        return result;
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
