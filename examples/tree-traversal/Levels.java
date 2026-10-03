import java.util.*;

public class Levels {
    static final class Node {
        final int value;
        Node left, right;
        Node(int value) { this.value = value; }
        public String toString() { return Integer.toString(value); } // Для трассы, не для сравнения личности.
    }
    static Node n(int value, Node left, Node right) {
        Node node = new Node(value); node.left = left; node.right = right; return node;
    }
    static Node sample() {
        return n(8, n(3, new Node(1), new Node(6)), new Node(10));
    }
    static Node chain(int length, boolean left) {
        Node root = null;
        for (int i = length - 1; i >= 0; i--) root = left ? n(i, root, null) : n(i, null, root);
        return root;
    }
    static List<List<Integer>> solve(Node root) {
        List<List<Integer>> result = new ArrayList<>();
        Deque<Node> queue = new ArrayDeque<>();
        if (root != null) queue.addLast(root);
        while (!queue.isEmpty()) {
            int size = queue.size(); // Фиксируем границу уровня до добавления детей.
            List<Integer> row = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                Node node = queue.removeFirst(); row.add(node.value);
                if (node.left != null) queue.addLast(node.left);
                if (node.right != null) queue.addLast(node.right);
            }
            result.add(row);
        }
        return result;
    }

    public static void main(String[] args) {
        check(null, List.of());
        check(new Node(9), List.of(List.of(9)));
        check(sample(), List.of(List.of(8), List.of(3, 10), List.of(1, 6)));
        check(n(7, new Node(7), new Node(7)), List.of(List.of(7), List.of(7, 7)));
        check(chain(3, false), List.of(List.of(0), List.of(1), List.of(2)));
        check(n(4, n(2, null, new Node(3)), n(8, new Node(6), null)), List.of(List.of(4), List.of(2, 8), List.of(3, 6)));
        check(n(0, new Node(Integer.MIN_VALUE), new Node(Integer.MAX_VALUE)), List.of(List.of(0), List.of(Integer.MIN_VALUE, Integer.MAX_VALUE)));
        List<List<Integer>> expected = new ArrayList<>();
        for (int i = 0; i < 10000; i++) expected.add(List.of(i));
        check(chain(10000, false), expected);
    }
    record Links(Node left, Node right) {}
    static Map<Node, Links> snapshot(Node root) {
        Map<Node, Links> saved = new IdentityHashMap<>();
        Deque<Node> queue = new ArrayDeque<>(); if (root != null) queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (saved.put(node, new Links(node.left, node.right)) != null) throw new AssertionError("not a tree");
            if (node.left != null) queue.addLast(node.left);
            if (node.right != null) queue.addLast(node.right);
        }
        return saved;
    }
    static void unchanged(Map<Node, Links> saved) {
        saved.forEach((node, links) -> {
            if (node.left != links.left() || node.right != links.right()) throw new AssertionError("links changed");
        });
    }
    static void check(Node root, List<List<Integer>> want) {
        Map<Node, Links> saved = snapshot(root); List<List<Integer>> got = solve(root);
        if (!got.equals(want)) throw new AssertionError("wrong levels"); unchanged(saved);
        System.out.println(got.size() > 20 ? "OK levels=" + got.size() + " first=" + got.getFirst() + " last=" + got.getLast() : "OK " + got);
    }
}
