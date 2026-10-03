import java.util.*;

public class EvalRpn {
    static int solve(String[] tokens) {
        var values = new ArrayDeque<Integer>();
        for (String token : tokens) {
            if (token.equals("+") || token.equals("-") || token.equals("*") || token.equals("/")) {
                if (values.size() < 2) throw new IllegalArgumentException("missing operand");
                int right = values.pop(); // Порядок важен для минуса и деления.
                int left = values.pop();
                long result = switch (token) {
                    case "+" -> (long) left + right;
                    case "-" -> (long) left - right;
                    case "*" -> (long) left * right;
                    default -> (long) left / right;
                };
                values.push(Math.toIntExact(result));
            } else values.push(Integer.parseInt(token));
        }
        if (values.size() != 1) throw new IllegalArgumentException("not one result");
        return values.pop();
    }

    public static void main(String[] args) {
        check(solve(new String[]{"8", "3", "-", "2", "*"}), 10);
        check(solve(new String[]{"8", "3", "2", "*", "-"}), 2);
        check(solve(new String[]{"3", "8", "-", "2", "/"}), -2);
        check(solve(new String[]{"-11"}), -11);
        check(solve(new String[]{"0"}), 0);
        check(solve(new String[]{"7", "3", "/"}), 2);
        check(solve(new String[]{"10", "6", "9", "3", "+", "-11", "*", "/", "*", "17", "+", "5", "+"}), 22);
        fails(new String[]{"8", "-"}, IllegalArgumentException.class);
        fails(new String[]{"8", "3"}, IllegalArgumentException.class);
        fails(new String[0], IllegalArgumentException.class);
        fails(new String[]{"8", "0", "/"}, ArithmeticException.class);
        fails(new String[]{"2147483647", "1", "+"}, ArithmeticException.class);
        fails(new String[]{"-2147483648", "-1", "/"}, ArithmeticException.class);
        fails(new String[]{"wrong"}, NumberFormatException.class);
    }

    static void check(int got, int want) {
        if (got != want) throw new AssertionError("got " + got + ", want " + want);
        System.out.println("OK " + got);
    }

    static void fails(String[] tokens, Class<? extends RuntimeException> want) {
        try { solve(tokens); }
        catch (RuntimeException got) {
            if (got.getClass() != want) throw new AssertionError("got " + got.getClass() + ", want " + want);
            System.out.println("OK " + got.getClass().getSimpleName());
            return;
        }
        throw new AssertionError("expected " + want);
    }
}
