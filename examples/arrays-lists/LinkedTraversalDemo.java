public class LinkedTraversalDemo {
    // Учебная двусвязная цепочка. JDK не публикует счётчик переходов.
    static class Node {
        final int value;
        Node prev, next;
        Node(int value) { this.value = value; }
    }
    final int size;
    Node first, last;
    long links;
    LinkedTraversalDemo(int n) {
        size = n;
        for (int i = 0; i < n; i++) {
            Node node = new Node(i);
            node.prev = last;
            if (last == null) first = node; else last.next = node;
            last = node;
        }
    }
    int get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        Node node;
        if (index < size / 2) { // Как node(index) в LinkedList JDK 21.
            node = first;
            for (int j = 0; j < index; j++) { node = node.next; links++; }
        } else {
            node = last;
            for (int j = size - 1; j > index; j--) { node = node.prev; links++; }
        }
        return node.value;
    }
    public static void main(String[] args) {
        var chain = new LinkedTraversalDemo(1000);
        long sum = 0;
        for (int i = 0; i < chain.size; i++) sum += chain.get(i);
        System.out.println("indexed: sum=" + sum + " links=" + chain.links);
        chain.links = 0; sum = 0;
        for (Node p = chain.first; p != null; p = p.next) {
            sum += p.value;
            chain.links++; // Чтение next; последнее ведёт к null.
        }
        System.out.println("one pass: sum=" + sum + " links=" + chain.links);
    }
}
