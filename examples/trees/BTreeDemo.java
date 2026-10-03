import java.util.ArrayList;
import java.util.List;

public class BTreeDemo {
    // Минимальная степень t=2: у полного узла три ключа, четыре ребёнка.
    static final class Node {
        final List<Integer> keys = new ArrayList<>();
        final List<Node> children = new ArrayList<>();
        boolean leaf() { return children.isEmpty(); }
    }
    static final class Tree {
        Node root = new Node();
        boolean trace;
        void put(int key) {
            if (contains(root, key)) return; // Демо хранит множество уникальных ключей.
            if (root.keys.size() == 3) {
                Node parent = new Node();
                parent.children.add(root);
                root = parent;
                if (trace) print("new root", root);
                split(parent, 0);
                if (trace) print("split", root);
            }
            insert(root, key);
            if (trace) print("inserted", root);
        }
        boolean contains(Node node, int key) {
            int i = 0;
            while (i < node.keys.size() && key > node.keys.get(i)) i++;
            if (i < node.keys.size() && key == node.keys.get(i)) return true;
            return !node.leaf() && contains(node.children.get(i), key);
        }
        void split(Node parent, int at) {
            Node left = parent.children.get(at);
            Node right = new Node();
            int middle = left.keys.get(1);
            right.keys.add(left.keys.get(2));
            left.keys.subList(1, 3).clear(); // Слева остаётся младший ключ.
            if (!left.leaf()) {
                right.children.addAll(left.children.subList(2, 4));
                left.children.subList(2, 4).clear();
            }
            parent.keys.add(at, middle); // Медиана переходит в родителя.
            parent.children.add(at + 1, right);
        }
        void insert(Node node, int key) {
            int i = 0;
            while (i < node.keys.size() && key > node.keys.get(i)) i++;
            if (node.leaf()) {
                node.keys.add(i, key);
                return;
            }
            if (node.children.get(i).keys.size() == 3) {
                split(node, i); // Спускаемся только в неполный узел.
                if (key > node.keys.get(i)) i++;
            }
            insert(node.children.get(i), key);
        }
    }
    static String shape(Node node) {
        if (node.leaf()) return node.keys.toString();
        return node.keys + "{" + node.children.stream()
                .map(BTreeDemo::shape).reduce((a, b) -> a + "," + b).orElse("") + "}";
    }
    static void print(String stage, Node node) {
        System.out.println(stage + ": " + shape(node));
    }
    public static void main(String[] args) {
        var tree = new Tree();
        for (int key = 1; key <= 3; key++) tree.put(key);
        print("before", tree.root);
        tree.trace = true;
        tree.put(4);
        tree.trace = false;
        for (int key = 5; key <= 7; key++) tree.put(key);
        print("after 1..7", tree.root);
        System.out.println("contains 4=" + tree.contains(tree.root, 4)
                + " contains 8=" + tree.contains(tree.root, 8));
    }
}
