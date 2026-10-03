import java.util.*;

public class ReverseDemo {
    static final class Node {
        final String id; // Имя объекта для трассы; совпадение value не означает один объект.
        final int value;
        Node next;
        Node(String id, int value) { this.id = id; this.value = value; }
    }

    static void reset(Node head) { head = null; } // Присваивание локальному параметру.
    static void cut(Node head) { head.next = null; } // Изменение общего объекта.

    static Node reverse(Node head, boolean trace) {
        Node prev = null, current = head;
        while (current != null) {
            Node next = current.next; // Сохраняем продолжение до перезаписи поля.
            if (trace) System.out.println("save current=" + id(current) + " next=" + id(next));
            current.next = prev; // Один узел переходит в развёрнутую часть.
            if (trace) System.out.println("link " + id(current) + ".next=" + id(prev));
            prev = current;
            current = next;
            if (trace) System.out.println("prev=" + id(prev) + " current=" + id(current));
        }
        return prev; // Новая голова — последний обработанный узел.
    }
    static Node wrongReverse(Node head) {
        Node prev = null, current = head;
        while (current != null) {
            current.next = prev;
            Node next = current.next; // Ошибка: исходное продолжение уже потеряно.
            prev = current;
            current = next;
        }
        return prev;
    }

    public static void main(String[] args) {
        System.out.println("== local references and fields ==");
        Node[] first = nodes(10, 20, 30);
        Node head = first[0], alias = first[1], cursor = head;
        cursor = cursor.next;
        reset(head);
        System.out.println("cursor=" + id(cursor) + " head=" + id(head));
        cut(head);
        System.out.println("head after cut=" + show(head));
        System.out.println("saved alias=" + show(alias));

        System.out.println("== reverse reuses nodes ==");
        Node[] original = nodes(10, 20, 30);
        Node oldHead = original[0], oldMiddle = original[1];
        Node reversed = reverse(oldHead, true);
        System.out.println("new head is original C=" + (reversed == original[2]));
        System.out.println("result=" + show(reversed));
        System.out.println("old head=" + show(oldHead));
        System.out.println("old middle=" + show(oldMiddle));

        System.out.println("== wrong order loses remainder ==");
        Node[] broken = nodes(10, 20, 30);
        System.out.println("wrong result=" + show(wrongReverse(broken[0])));
        System.out.println("saved B still sees=" + show(broken[1]));

        System.out.println("== empty and single ==");
        System.out.println("empty=" + id(reverse(null, false)));
        Node single = new Node("S", 7);
        System.out.println("single reused=" + (reverse(single, false) == single) + " next=" + id(single.next));
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
