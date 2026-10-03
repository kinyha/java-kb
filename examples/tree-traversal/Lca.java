import java.util.*;

public class Lca {
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
    static Node solve(Node root, Node p, Node q) {
        if (root == null) return null;
        Map<Node, Node> parent = new IdentityHashMap<>();
        Deque<Node> stack = new ArrayDeque<>();
        parent.put(root, null); stack.push(root);
        while (!stack.isEmpty() && (!parent.containsKey(p) || !parent.containsKey(q))) {
            Node node = stack.pop();
            if (node.right != null) { parent.put(node.right, node); stack.push(node.right); }
            if (node.left != null) { parent.put(node.left, node); stack.push(node.left); }
        }
        if (!parent.containsKey(p) || !parent.containsKey(q)) return null; // Наличие обеих целей доказано.
        Set<Node> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node = p; node != null; node = parent.get(node)) ancestors.add(node);
        for (Node node = q; node != null; node = parent.get(node))
            if (ancestors.contains(node)) return node; // Первый общий по пути вверх из q — самый глубокий.
        throw new AssertionError("both nodes have the same root");
    }

    public static void main(String[] args) {
        Node root = sample(), one = root.left.left, six = root.left.right;
        check(root, one, six, root.left);
        check(root, root.left, six, root.left);
        check(root, six, six, six);
        check(root, one, root.right, root);
        check(root, one, new Node(6), null);
        check(root, new Node(1), new Node(6), null);
        check(null, one, six, null);
        Node left = new Node(7), right = new Node(7), repeated = n(9, left, right);
        check(repeated, left, right, repeated);
        Node deep = chain(10000, false), a = deep.right, b = a;
        while (b.right != null) b = b.right;
        check(deep, a, b, a);
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
    static void check(Node root, Node p, Node q, Node want) {
        Map<Node, Links> saved = snapshot(root); Node got = solve(root, p, q);
        if (got != want) throw new AssertionError("wrong ancestor identity"); unchanged(saved);
        System.out.println("OK nodes=" + saved.size() + " p=" + p + " q=" + q + " lca=" + got);
    }
}
