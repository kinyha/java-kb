import java.util.*;

public class MergeDemo {
    static final class Node {
        final String id; // Имя объекта для трассы; совпадение value не означает один объект.
        final int value;
        Node next;
        Node(String id, int value) { this.id = id; this.value = value; }
    }

    static Node merge(Node a, Node b, boolean trace) {
        Node dummy = new Node("DUMMY", 0), tail = dummy;
        while (a != null && b != null) {
            Node chosen;
            if (a.value <= b.value) { chosen = a; a = a.next; } // При равенстве первый список идёт раньше.
            else { chosen = b; b = b.next; }
            tail.next = chosen; // Присоединяем исходный объект, не копируем его value.
            tail = chosen;
            if (trace) System.out.println("take=" + id(chosen) + " a=" + id(a) + " b=" + id(b) + " tail=" + id(tail));
        }
        tail.next = a != null ? a : b; // Оставшаяся цепочка уже отсортирована.
        if (trace) System.out.println("append=" + id(tail.next) + " tail=" + id(tail));
        return dummy.next; // Сам служебный узел не входит в результат.
    }
    public static void main(String[] args) {
        System.out.println("== sorted merge rewires original nodes ==");
        Node a1 = new Node("A1", 1), a3 = new Node("A3", 3), a5 = new Node("A5", 5);
        a1.next = a3; a3.next = a5;
        Node b2 = new Node("B2", 2), b3 = new Node("B3", 3), b6 = new Node("B6", 6);
        b2.next = b3; b3.next = b6;
        Node merged = merge(a1, b2, true);
        System.out.println("merged=" + show(merged));
        System.out.println("head reused=" + (merged == a1));
        System.out.println("equal values A3 before B3=" + (a3.next == b3));
        System.out.println("old B2 alias=" + show(b2));

        System.out.println("== empty inputs reuse remainder ==");
        Node only = new Node("X", 5);
        System.out.println("reused=" + (merge(null, only, false) == only));
        System.out.println("both empty=" + show(merge(null, null, false)));

        System.out.println("== shared suffix violates ownership ==");
        Node shared = new Node("S", 3);
        Node a = new Node("A", 1), b = new Node("B", 2);
        a.next = shared; b.next = shared;
        Node broken = merge(a, b, false); // Вызов намеренно нарушает условие отдельных списков.
        System.out.println("shared result=" + show(broken));
        System.out.println("S.next is S=" + (shared.next == shared));
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
