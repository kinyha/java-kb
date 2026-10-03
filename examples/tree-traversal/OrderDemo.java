import java.util.*;

public class OrderDemo {
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
    static void walk(Node node, String order, List<Integer> result) {
        if (node == null) return;
        if (order.equals("pre")) result.add(node.value); // Первый момент: до обоих детей.
        walk(node.left, order, result);
        if (order.equals("in")) result.add(node.value); // Второй момент: левая ветка уже обработана.
        walk(node.right, order, result);
        if (order.equals("post")) result.add(node.value); // Третий момент: результаты детей готовы.
    }
    static List<Integer> inorder(Node root, boolean trace) {
        List<Integer> result = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        Node current = root;
        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.push(current); // Запомнили возврат после левого поддерева.
                current = current.left;
                if (trace) System.out.println("push stack=" + stack + " current=" + current);
            }
            current = stack.pop();
            result.add(current.value); // Левая ветка завершена: теперь сам узел.
            current = current.right;
            if (trace) System.out.println("emit=" + result.getLast() + " stack=" + stack + " current=" + current);
        }
        return result;
    }
    record Action(Node node, boolean emit) {}
    static List<Integer> postorder(Node root) {
        List<Integer> result = new ArrayList<>();
        Deque<Action> stack = new ArrayDeque<>();
        if (root != null) stack.push(new Action(root, false));
        while (!stack.isEmpty()) {
            Action action = stack.pop(); Node node = action.node();
            if (action.emit()) { result.add(node.value); continue; }
            stack.push(new Action(node, true)); // Обработка родителя останется под детьми.
            if (node.right != null) stack.push(new Action(node.right, false));
            if (node.left != null) stack.push(new Action(node.left, false)); // LIFO: левый ребёнок идёт последним в push.
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("== processing moment changes the order ==");
        Node root = sample();
        for (String order : List.of("pre", "in", "post")) {
            List<Integer> result = new ArrayList<>(); walk(root, order, result);
            System.out.println(order + "=" + result);
        }
        List<Integer> arbitrary = new ArrayList<>();
        walk(n(8, new Node(7), new Node(1)), "in", arbitrary);
        System.out.println("non-BST inorder=" + arbitrary);

        System.out.println("== stack stores pending returns ==");
        System.out.println("iterative inorder=" + inorder(root, true));
        System.out.println("iterative postorder=" + postorder(root));
        System.out.println("empty=" + inorder(null, false));

        System.out.println("== deep chain without recursive calls ==");
        List<Integer> deep = inorder(chain(10000, false), false);
        System.out.println("nodes=" + deep.size() + " first=" + deep.getFirst() + " last=" + deep.getLast());
    }
}
