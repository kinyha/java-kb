import java.util.*;

public class Inorder {
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
    static List<Integer> solve(Node root) {
        List<Integer> result = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        Node current = root;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.push(current); // Запомнили возврат после левого поддерева.
                current = current.left;
            }
            current = stack.pop();
            result.add(current.value); // Левая ветка завершена: теперь сам узел.
            current = current.right;
        }
        return result;
    }

    public static void main(String[] args) {
        check(null, List.of());
        check(new Node(9), List.of(9));
        check(sample(), List.of(1, 3, 6, 8, 10));
        check(n(8, new Node(7), new Node(1)), List.of(7, 8, 1));
        check(n(7, new Node(7), new Node(7)), List.of(7, 7, 7));
        check(n(0, null, n(1, null, new Node(2))), List.of(0, 1, 2));
        check(n(2, n(1, new Node(0), null), null), List.of(0, 1, 2));
        Node deep = chain(10000, true); List<Integer> expected = new ArrayList<>();
        for (int i = 9999; i >= 0; i--) expected.add(i);
        check(deep, expected);
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
    static void check(Node root, List<Integer> want) {
        Map<Node, Links> saved = snapshot(root); List<Integer> got = solve(root);
        if (!got.equals(want)) throw new AssertionError("wrong order"); unchanged(saved);
        System.out.println(got.size() > 20 ? "OK nodes=" + got.size() + " first=" + got.getFirst() + " last=" + got.getLast() : "OK " + got);
    }
}
