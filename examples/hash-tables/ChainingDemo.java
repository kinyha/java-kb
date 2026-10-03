import java.util.Locale;
import java.util.Objects;

public class ChainingDemo {
    static final class Node {
        final int key;
        String value;
        Node next;
        Node(int key, String value, Node next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    static final class Table {
        Node[] buckets = new Node[4];
        int size;
        int checks;

        String get(int key) {
            checks = 0;
            int index = Math.floorMod(key, buckets.length);
            for (Node node = buckets[index]; node != null; node = node.next) {
                checks++;
                if (node.key == key) return node.value;
            }
            return null;
        }

        String put(int key, String value) {
            Objects.requireNonNull(value);
            int index = Math.floorMod(key, buckets.length);
            for (Node node = buckets[index]; node != null; node = node.next) {
                if (node.key == key) {
                    String old = node.value;
                    node.value = value; // Замена не добавляет запись и не растит size.
                    return old;
                }
            }
            buckets[index] = new Node(key, value, buckets[index]);
            size++;
            if (size > buckets.length * 3 / 4) grow(); // Учебный порог 0.75.
            return null;
        }

        void grow() {
            Node[] old = buckets;
            buckets = new Node[Math.multiplyExact(old.length, 2)];
            System.out.println("resize " + old.length + "->" + buckets.length);
            for (Node head : old) {
                for (Node node = head; node != null;) {
                    Node next = node.next; // Сохраняем старый путь до смены ссылки.
                    int index = Math.floorMod(node.key, buckets.length);
                    node.next = buckets[index];
                    buckets[index] = node;
                    System.out.println("move " + node.key + " -> bucket " + index);
                    node = next;
                }
            }
        }

        String snapshot() {
            var result = new StringBuilder(String.format(Locale.ROOT,
                    "m=%d n=%d alpha=%.2f", buckets.length, size,
                    (double) size / buckets.length));
            for (int i = 0; i < buckets.length; i++) {
                if (buckets[i] == null) continue;
                result.append("\n").append(i).append(": ");
                for (Node node = buckets[i]; node != null; node = node.next) {
                    result.append(node.key).append(':').append(node.value);
                    if (node.next != null) result.append(" -> ");
                }
            }
            return result.toString();
        }
    }

    public static void main(String[] args) {
        var table = new Table();
        table.put(1, "PAID");
        table.put(5, "PENDING");
        table.put(9, "REFUNDED");
        System.out.println("== chain ==");
        System.out.println(table.snapshot());
        System.out.println("get 5=" + table.get(5) + " checks=" + table.checks);
        System.out.println("get 13=" + table.get(13) + " checks=" + table.checks);
        System.out.println("replace 5=" + table.put(5, "CAPTURED")
                + " size=" + table.size);
        System.out.println("== growth ==");
        table.put(13, "NEW");
        System.out.println(table.snapshot());
        System.out.println("get 13=" + table.get(13) + " checks=" + table.checks);
    }
}
