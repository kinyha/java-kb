import java.util.*;

public class BracketDemo {
    static boolean balanced(String input, boolean trace) {
        var pending = new ArrayDeque<Character>();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '(' -> pending.push(')'); // Храним ожидаемый закрывающий символ.
                case '[' -> pending.push(']');
                case '{' -> pending.push('}');
                case ')', ']', '}' -> {
                    if (pending.isEmpty() || pending.pop() != c) return false;
                }
                default -> throw new IllegalArgumentException("not a bracket");
            }
            if (trace) System.out.println("char=" + c + " expected(top first)=" + pending.stream().map(ch -> "'" + ch + "'").toList());
        }
        return pending.isEmpty(); // Оставшиеся ожидания означают незакрытые скобки.
    }

    static boolean countsMatch(String input) {
        int round = 0, square = 0, curly = 0;
        for (char c : input.toCharArray()) {
            switch (c) {
                case '(' -> round++;
                case ')' -> round--;
                case '[' -> square++;
                case ']' -> square--;
                case '{' -> curly++;
                case '}' -> curly--;
                default -> throw new IllegalArgumentException("not a bracket");
            }
        }
        return round == 0 && square == 0 && curly == 0; // Теряет порядок вложенности.
    }

    public static void main(String[] args) {
        System.out.println("== equal counts, different nesting ==");
        for (String s : new String[]{"([])", "([)]", ")(", "(("}) {
            System.out.println(s + " counts=" + countsMatch(s) + " stack=" + balanced(s, false));
        }

        System.out.println("== pending closers ==");
        System.out.println("balanced=" + balanced("{[()]}", true));
        System.out.println("empty=" + balanced("", false));

        System.out.println("== one end for both operations ==");
        var mixed = new ArrayDeque<Integer>();
        mixed.push(10);
        mixed.addLast(20); // Другой конец: эта пара операций уже не даёт LIFO.
        System.out.println("mixed pop=" + mixed.pop());
        var stack = new ArrayDeque<Integer>();
        stack.push(10);
        stack.push(20);
        System.out.println("stack pop=" + stack.pop());
        System.out.println("peek=" + stack.peek() + " size=" + stack.size());
        stack.pop();
        System.out.println("empty peek=" + stack.peek());
        try { stack.pop(); }
        catch (NoSuchElementException e) { System.out.println("empty pop=" + e.getClass().getSimpleName()); }
    }
}
