import java.util.*;

public class ValidBrackets {
    static boolean solve(String input) {
        var pending = new ArrayDeque<Character>();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '(' -> pending.push(')');
                case '[' -> pending.push(']');
                case '{' -> pending.push('}');
                default -> {
                    if (pending.isEmpty() || pending.pop() != c) return false;
                }
            }
        }
        return pending.isEmpty();
    }

    public static void main(String[] args) {
        check(solve(""), true);
        check(solve("{[()]}"), true);
        check(solve("()[]{}"), true);
        check(solve("([)]"), false);
        check(solve(")("), false);
        check(solve("(("), false);
        check(solve("("), false);
        check(solve("]"), false);
        check(solve("(())"), true);
        check(solve("{[()]}]"), false);
    }

    static void check(boolean got, boolean want) {
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }
}
