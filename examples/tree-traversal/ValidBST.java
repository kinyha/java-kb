import java.util.*;

public class ValidBST {
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
    record Bound(Node node, long low, long high) {}
    static boolean solve(Node root) {
        Deque<Bound> stack = new ArrayDeque<>();
        if (root != null) stack.push(new Bound(root, Long.MIN_VALUE, Long.MAX_VALUE));
        while (!stack.isEmpty()) {
            Bound bound = stack.pop(); Node node = bound.node();
            boolean ok = bound.low() < node.value && node.value < bound.high();
            if (!ok) return false; // Граница сохраняет ограничения всех предков.
            if (node.right != null) stack.push(new Bound(node.right, node.value, bound.high()));
            if (node.left != null) stack.push(new Bound(node.left, bound.low(), node.value));
        }
        return true;
    }

    public static void main(String[] args) {
        check(null, true);
        check(sample(), true);
        check(n(8, new Node(3), n(10, new Node(7), null)), false);
        check(n(8, n(3, null, new Node(9)), new Node(10)), false);
        check(n(8, null, new Node(8)), false);
        check(n(0, new Node(Integer.MIN_VALUE), new Node(Integer.MAX_VALUE)), true);
        check(new Node(Integer.MIN_VALUE), true);
        check(new Node(Integer.MAX_VALUE), true);
        check(chain(10000, false), true);
        check(chain(10000, true), false);
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
    static void check(Node root, boolean want) {
        Map<Node, Links> saved = snapshot(root); boolean got = solve(root);
        if (got != want) throw new AssertionError("wrong BST answer"); unchanged(saved);
        System.out.println("OK nodes=" + saved.size() + " valid=" + got);
    }
}
