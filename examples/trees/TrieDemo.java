import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class TrieDemo {
    static final class Node {
        boolean terminal;
        final TreeMap<Character, Node> children = new TreeMap<>();
    }
    static final class Trie {
        final Node root = new Node();
        Node follow(String text) {
            Node node = root;
            for (int i = 0; i < text.length() && node != null; i++)
                node = node.children.get(text.charAt(i));
            return node;
        }
        void put(String text) {
            Node node = root;
            for (int i = 0; i < text.length(); i++)
                node = node.children.computeIfAbsent(text.charAt(i), c -> new Node());
            node.terminal = true; // Путь существовал и до добавления слова-префикса.
        }
        boolean contains(String text) {
            Node node = follow(text);
            return node != null && node.terminal;
        }
        List<String> prefix(String text) {
            var result = new ArrayList<String>();
            collect(follow(text), new StringBuilder(text), result);
            return result;
        }
        void collect(Node node, StringBuilder word, List<String> result) {
            if (node == null) return;
            if (node.terminal) result.add(word.toString());
            for (var edge : node.children.entrySet()) {
                word.append(edge.getKey());
                collect(edge.getValue(), word, result);
                word.setLength(word.length() - 1); // Возвращаем общий префикс перед соседней веткой.
            }
        }
        void remove(String text) { remove(root, text, 0); }
        boolean remove(Node node, String text, int at) {
            if (at == text.length()) node.terminal = false;
            else {
                char letter = text.charAt(at);
                Node child = node.children.get(letter);
                if (child != null && remove(child, text, at + 1)) node.children.remove(letter);
            }
            return !node.terminal && node.children.isEmpty(); // Только бесполезный узел можно отсечь.
        }
    }
    public static void main(String[] args) {
        var trie = new Trie();
        trie.put("PAYOUT");
        trie.put("PAID");
        System.out.println("before PAY: exact=" + trie.contains("PAY")
                + " prefix=" + trie.prefix("PAY"));
        trie.put("PAY");
        System.out.println("after PAY: exact=" + trie.contains("PAY")
                + " prefix=" + trie.prefix("PAY"));
        System.out.println("PA exact=" + trie.contains("PA") + " prefix=" + trie.prefix("PA"));
        trie.remove("PAY"); // Общий путь для PAYOUT остаётся.
        System.out.println("remove PAY: exact=" + trie.contains("PAY")
                + " PAYOUT=" + trie.contains("PAYOUT"));
        trie.remove("PAYOUT");
        System.out.println("remove PAYOUT: prefix PAY=" + trie.prefix("PAY")
                + " PAID=" + trie.contains("PAID"));
        trie.put(""); // Пустое слово отмечает корень.
        System.out.println("empty exact=" + trie.contains(""));
    }
}
