import java.util.*;

public class BreadthDemo {
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
    static List<List<Integer>> levels(Node root, boolean trace) {
        List<List<Integer>> result = new ArrayList<>();
        Deque<Node> queue = new ArrayDeque<>();
        if (root != null) queue.addLast(root);
        while (!queue.isEmpty()) {
            int size = queue.size(); // Фиксируем границу уровня до добавления детей.
            List<Integer> row = new ArrayList<>();
            if (trace) System.out.println("level=" + result.size() + " size=" + size + " queue=" + queue);
            for (int i = 0; i < size; i++) {
                Node node = queue.removeFirst(); row.add(node.value);
                if (node.left != null) queue.addLast(node.left);
                if (node.right != null) queue.addLast(node.right);
                if (trace) System.out.println("take=" + node.value + " remaining=" + (size - i - 1) + " queue=" + queue);
            }
            result.add(row);
        }
        return result;
    }
    static int height(Node node) {
        if (node == null) return 0; // Считаем узлы: лист имеет глубину 1.
        int left = height(node.left), right = height(node.right);
        return 1 + Math.max(left, right); // Обработка после детей — postorder.
    }
    static int wrongMin(Node node) {
        if (node == null) return 0;
        return 1 + Math.min(wrongMin(node.left), wrongMin(node.right)); // Ошибка: пустая ссылка не лист.
    }
    static int minDepth(Node root) {
        if (root == null) return 0;
        Deque<Node> queue = new ArrayDeque<>(); queue.addLast(root);
        int depth = 0;
        while (!queue.isEmpty()) {
            int size = queue.size(); depth++;
            for (int i = 0; i < size; i++) {
                Node node = queue.removeFirst();
                if (node.left == null && node.right == null) return depth; // Первый настоящий лист.
                if (node.left != null) queue.addLast(node.left);
                if (node.right != null) queue.addLast(node.right);
            }
        }
        throw new AssertionError("unreachable for a finite tree");
    }

    public static void main(String[] args) {
        System.out.println("== queue mixes two adjacent levels ==");
        Node root = sample();
        System.out.println("levels=" + levels(root, true));
        System.out.println("empty levels=" + levels(null, false));

        System.out.println("== null children are not queue elements ==");
        try { new ArrayDeque<Node>().addLast(null); }
        catch (NullPointerException e) { System.out.println("enqueue null=" + e.getClass().getSimpleName()); }

        System.out.println("== a missing child is not a shorter leaf path ==");
        Node thin = chain(3, false);
        System.out.println("sample height=" + height(root) + " bfs height=" + levels(root, false).size());
        System.out.println("chain height=" + height(thin) + " wrong min=" + wrongMin(thin) + " actual min=" + minDepth(thin));
        System.out.println("empty height=" + height(null) + " leaf height=" + height(new Node(9)));
        System.out.println("deep BFS levels=" + levels(chain(10000, false), false).size());
    }
}
