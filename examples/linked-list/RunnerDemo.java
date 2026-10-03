import java.util.*;

public class RunnerDemo {
    static final class Node {
        final String id; // Имя объекта для трассы; совпадение value не означает один объект.
        final int value;
        Node next;
        Node(String id, int value) { this.id = id; this.value = value; }
    }

    static Node middle(Node head) {
        Node slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        return slow; // При чётной длине — вторая из двух середин.
    }

    static Node entry(Node head, boolean trace) {
        Node slow = head, fast = head;
        int step = 0;
        while (fast != null && fast.next != null) { // Проверки защищают оба перехода fast.
            slow = slow.next;
            fast = fast.next.next;
            if (trace) System.out.println("step=" + ++step + " slow=" + id(slow) + " fast=" + id(fast));
            if (slow == fast) { // Сравниваем ссылки, не суммы платежей.
                Node finder = head;
                if (trace) System.out.println("reset finder=" + id(finder) + " meet=" + id(slow));
                while (finder != slow) {
                    finder = finder.next;
                    slow = slow.next;
                    if (trace) System.out.println("finder=" + id(finder) + " meet=" + id(slow));
                }
                return finder;
            }
        }
        return null;
    }
    static boolean wrongValueCycle(Node head) {
        Node slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (fast != null && slow.value == fast.value) return true; // Ошибка: разные объекты имеют одинаковые value.
        }
        return false;
    }

    static Node remove(Node head, int n, boolean trace) {
        Node dummy = new Node("DUMMY", 0);
        dummy.next = head;
        Node slow = dummy, fast = dummy;
        for (int i = 0; i <= n; i++) {
            if (fast == null) throw new IllegalArgumentException("n exceeds length");
            fast = fast.next; // n+1 переходов: slow в конце окажется перед нужным узлом.
        }
        if (trace) System.out.println("gap=" + (n + 1) + " slow=" + id(slow) + " fast=" + id(fast));
        while (fast != null) {
            slow = slow.next;
            fast = fast.next;
            if (trace) System.out.println("slow=" + id(slow) + " fast=" + id(fast));
        }
        if (trace) System.out.println("bypass=" + id(slow.next));
        slow.next = slow.next.next; // Меняем входящую ссылку, value узлов не трогаем.
        return dummy.next;
    }
    public static void main(String[] args) {
        System.out.println("== middle depends on loop convention ==");
        System.out.println("length 5 middle=" + id(middle(head(nodes(10, 20, 30, 40, 50)))));
        System.out.println("length 4 middle=" + id(middle(head(nodes(10, 20, 30, 40)))));
        System.out.println("empty middle=" + id(middle(null)));

        System.out.println("== meet is not the entry ==");
        Node[] ring = nodes(10, 20, 30, 40, 50);
        ring[4].next = ring[2]; // E ведёт в C: два узла до цикла, три узла в цикле.
        System.out.println("ring=" + show(ring[0]));
        System.out.println("entry=" + id(entry(ring[0], true)));
        Node equalValues = head(nodes(7, 7, 7));
        System.out.println("equal values wrong=" + wrongValueCycle(equalValues));
        System.out.println("equal values actual=" + (entry(equalValues, false) != null));
        Node self = new Node("S", 1); self.next = self;
        System.out.println("self entry=" + id(entry(self, false)));
        System.out.println("empty entry=" + id(entry(null, false)));

        System.out.println("== fixed gap locates predecessor ==");
        Node[] pending = nodes(10, 20, 30, 40, 50);
        Node without = remove(pending[0], 2, true);
        System.out.println("result=" + show(without));
        System.out.println("removed alias=" + show(pending[3]));

        System.out.println("== dummy can replace the head ==");
        Node[] pair = nodes(10, 20);
        Node newHead = remove(pair[0], 2, false);
        System.out.println("new head=" + show(newHead));
        System.out.println("old caller head=" + show(pair[0]));
        System.out.println("remove only=" + show(remove(new Node("S", 1), 1, false)));
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
