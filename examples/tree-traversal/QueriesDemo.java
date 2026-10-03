import java.util.*;

public class QueriesDemo {
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
    static boolean valid(Node root, boolean trace) {
        Deque<Bound> stack = new ArrayDeque<>();
        if (root != null) stack.push(new Bound(root, Long.MIN_VALUE, Long.MAX_VALUE));
        while (!stack.isEmpty()) {
            Bound bound = stack.pop(); Node node = bound.node();
            boolean ok = bound.low() < node.value && node.value < bound.high();
            if (trace) System.out.println("value=" + node.value + " bounds=(" + bound.low() + "," + bound.high() + ") ok=" + ok);
            if (!ok) return false; // Граница сохраняет ограничения всех предков.
            if (node.right != null) stack.push(new Bound(node.right, node.value, bound.high()));
            if (node.left != null) stack.push(new Bound(node.left, bound.low(), node.value));
        }
        return true;
    }
    static boolean local(Node node) {
        if (node == null) return true;
        return (node.left == null || node.left.value < node.value)
            && (node.right == null || node.right.value > node.value)
            && local(node.left) && local(node.right); // Ошибка: ограничения предков потерялись.
    }
    static Node lcaAssumingPresent(Node root, Node p, Node q) {
        if (root == null || root == p || root == q) return root;
        Node left = lcaAssumingPresent(root.left, p, q);
        Node right = lcaAssumingPresent(root.right, p, q);
        if (left != null && right != null) return root; // Цели пришли из разных веток.
        return left != null ? left : right;
    }
    static Node lcaChecked(Node root, Node p, Node q) {
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
        System.out.println("== grandchildren inherit the root boundary ==");
        Node invalid = n(8, new Node(3), n(10, new Node(7), null));
        System.out.println("local=" + local(invalid));
        System.out.println("global=" + valid(invalid, true));
        System.out.println("extremes valid=" + valid(n(0, new Node(Integer.MIN_VALUE), new Node(Integer.MAX_VALUE)), false));
        System.out.println("duplicate valid=" + valid(n(8, null, new Node(8)), false));

        System.out.println("== ancestor can be one of the targets ==");
        Node root = sample(), p = root.left.left, q = root.left.right;
        System.out.println("lca(1,6)=" + lcaChecked(root, p, q));
        System.out.println("lca(3,6)=" + lcaChecked(root, root.left, q));
        System.out.println("lca(6,6) same object=" + (lcaChecked(root, q, q) == q));

        System.out.println("== one found target does not prove two exist ==");
        Node absent = new Node(6);
        System.out.println("assuming present=" + lcaAssumingPresent(root, p, absent));
        System.out.println("checked=" + lcaChecked(root, p, absent));

        System.out.println("== equal values remain different nodes ==");
        Node left = new Node(7), right = new Node(7), duplicateRoot = n(9, left, right);
        System.out.println("lca is root=" + (lcaChecked(duplicateRoot, left, right) == duplicateRoot));
        System.out.println("foreign same value=" + lcaChecked(duplicateRoot, left, new Node(7)));
    }
}
