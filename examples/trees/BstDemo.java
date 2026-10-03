import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

public class BstDemo {
    static final class Node {
        int key;
        String value;
        Node left, right;
        Node(int key, String value) { this.key = key; this.value = value; }
    }
    static final class Tree {
        Node root;
        final List<Integer> path = new ArrayList<>();

        void put(int key, String value) { root = put(root, key, value); }
        Node put(Node node, int key, String value) {
            if (node == null) return new Node(key, value);
            if (key < node.key) node.left = put(node.left, key, value);
            else if (key > node.key) node.right = put(node.right, key, value);
            else node.value = value; // Равный ключ обновляет запись.
            return node;
        }
        String get(int key) {
            path.clear();
            for (Node node = root; node != null;) {
                path.add(node.key);
                if (key == node.key) return node.value;
                node = key < node.key ? node.left : node.right;
            }
            return null;
        }
        List<Integer> walk(String order) {
            var result = new ArrayList<Integer>();
            walk(root, order, result);
            return result;
        }
        void walk(Node node, String order, List<Integer> result) {
            if (node == null) return;
            if (order.equals("pre")) result.add(node.key);
            walk(node.left, order, result);
            if (order.equals("in")) result.add(node.key);
            walk(node.right, order, result);
            if (order.equals("post")) result.add(node.key);
        }
        List<Integer> breadthFirst() {
            var result = new ArrayList<Integer>();
            var queue = new ArrayDeque<Node>();
            if (root != null) queue.add(root);
            while (!queue.isEmpty()) {
                Node node = queue.remove();
                result.add(node.key);
                if (node.left != null) queue.add(node.left);
                if (node.right != null) queue.add(node.right);
            }
            return result;
        }
        List<Integer> range(int low, int high) {
            var result = new ArrayList<Integer>();
            range(root, low, high, result);
            return result;
        }
        void range(Node node, int low, int high, List<Integer> result) {
            if (node == null) return;
            if (low < node.key) range(node.left, low, high, result);
            if (low <= node.key && node.key <= high) result.add(node.key);
            if (node.key < high) range(node.right, low, high, result);
        }
        void remove(int key) { root = remove(root, key); }
        Node remove(Node node, int key) {
            if (node == null) return null;
            if (key < node.key) node.left = remove(node.left, key);
            else if (key > node.key) node.right = remove(node.right, key);
            else {
                if (node.left == null) return node.right; // В том числе лист: вернёт null.
                if (node.right == null) return node.left;
                Node successor = node.right;
                while (successor.left != null) successor = successor.left;
                node.key = successor.key;
                node.value = successor.value; // Переносим пару, а не один ключ.
                node.right = remove(node.right, successor.key);
            }
            return node;
        }
        int height() { return height(root); }
        int height(Node node) {
            return node == null ? 0 : 1 + Math.max(height(node.left), height(node.right));
        }
    }
    static Tree payments() {
        var tree = new Tree();
        for (int id : new int[]{8, 4, 12, 2, 6, 10, 14, 5, 7}) tree.put(id, "P" + id);
        return tree;
    }
    static void medians(Tree tree, int low, int high) {
        if (low > high) return;
        int mid = low + (high - low) / 2;
        tree.put(mid, "P" + mid);
        medians(tree, low, mid - 1);
        medians(tree, mid + 1, high);
    }
    public static void main(String[] args) {
        var tree = payments();
        System.out.println("== search ==");
        System.out.println("get 7=" + tree.get(7) + " path=" + tree.path);
        System.out.println("get 9=" + tree.get(9) + " path=" + tree.path);
        tree.put(6, "SETTLED");
        System.out.println("replace 6=" + tree.get(6) + " nodes=" + tree.walk("in").size());
        System.out.println("== walks ==");
        System.out.println("in=" + tree.walk("in"));
        System.out.println("pre=" + tree.walk("pre"));
        System.out.println("post=" + tree.walk("post"));
        System.out.println("bfs=" + tree.breadthFirst());
        System.out.println("range[5,11]=" + tree.range(5, 11));
        System.out.println("== deletion ==");
        tree.remove(8);
        System.out.println("remove 8 root=" + tree.root.key + " value=" + tree.root.value
                + " keys=" + tree.walk("in"));
        tree.remove(2); // Лист.
        tree.remove(12); // Теперь у 12 один ребёнок — 14.
        System.out.println("remove 2,12 keys=" + tree.walk("in"));
        System.out.println("== shape ==");
        var sorted = new Tree();
        for (int id = 1; id <= 31; id++) sorted.put(id, "P" + id);
        var balanced = new Tree();
        medians(balanced, 1, 31);
        System.out.println("sorted height=" + sorted.height() + " get 31=" + sorted.get(31)
                + " checks=" + sorted.path.size());
        System.out.println("medians height=" + balanced.height() + " get 31=" + balanced.get(31)
                + " checks=" + balanced.path.size());
    }
}
