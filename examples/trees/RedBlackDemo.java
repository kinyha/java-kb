public class RedBlackDemo {
    static final class Node {
        final int key;
        boolean red = true;
        Node left, right;
        Node(int key) { this.key = key; }
    }
    static final class Tree {
        Node root;
        boolean trace;
        boolean red(Node node) { return node != null && node.red; }
        void put(int key) {
            root = put(root, key);
            root.red = false; // Корень после операции всегда чёрный.
            if (trace) print("root black", root);
        }
        Node put(Node node, int key) {
            if (node == null) return new Node(key);
            if (key < node.key) node.left = put(node.left, key);
            else if (key > node.key) node.right = put(node.right, key);
            if (trace && node == root) print("inserted", node);
            if (red(node.right) && !red(node.left)) node = left(node);
            if (red(node.left) && red(node.left.left)) {
                node = right(node);
                if (trace) print("rotate right", node);
            }
            if (red(node.left) && red(node.right)) {
                node.red = !node.red;
                node.left.red = !node.left.red;
                node.right.red = !node.right.red;
                if (trace) print("flip colors", node);
            }
            return node;
        }
        Node right(Node top) {
            Node up = top.left;
            top.left = up.right; // Среднее поддерево остаётся между up и top.
            up.right = top;
            up.red = top.red;
            top.red = true;
            return up;
        }
        Node left(Node top) {
            Node up = top.right;
            top.right = up.left;
            up.left = top;
            up.red = top.red;
            top.red = true;
            return up;
        }
        int height(Node node) {
            return node == null ? 0 : 1 + Math.max(height(node.left), height(node.right));
        }
        int blackHeight(Node node) {
            if (node == null) return 1; // Чёрный NIL включаем в счётчик.
            if (red(node) && (red(node.left) || red(node.right)))
                throw new IllegalStateException("two reds");
            if (red(node.right)) throw new IllegalStateException("right red link");
            int left = blackHeight(node.left), right = blackHeight(node.right);
            if (left != right) throw new IllegalStateException("black height mismatch");
            return left + (node.red ? 0 : 1);
        }
    }
    static String shape(Node node) {
        if (node == null) return ".";
        return node.key + (node.red ? "R" : "B") + "("
                + shape(node.left) + "," + shape(node.right) + ")";
    }
    static void print(String stage, Node node) {
        System.out.println(stage + ": " + shape(node));
    }
    public static void main(String[] args) {
        var tree = new Tree();
        tree.put(3);
        tree.put(2);
        System.out.println("== repair ==");
        print("before", tree.root);
        tree.trace = true;
        tree.put(1);
        System.out.println("blackHeight=" + tree.blackHeight(tree.root));
        System.out.println("== sequential ==");
        var large = new Tree();
        for (int key = 1; key <= 1000; key++) large.put(key);
        System.out.println("n=1000 height=" + large.height(large.root)
                + " blackHeight=" + large.blackHeight(large.root)
                + " rootBlack=" + !large.root.red);
    }
}
