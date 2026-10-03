import java.util.*;

public class ExpressionDemo {
    static boolean operator(String token) {
        return token.equals("+") || token.equals("-") || token.equals("*") || token.equals("/");
    }

    static int apply(String op, int left, int right) {
        long result = switch (op) {
            case "+" -> (long) left + right;
            case "-" -> (long) left - right;
            case "*" -> (long) left * right;
            case "/" -> (long) left / right; // Деление к нулю; нулевой right даст ArithmeticException.
            default -> throw new IllegalArgumentException("unknown operator");
        };
        return Math.toIntExact(result); // Переполнение не превращается в другую сумму.
    }

    static int evaluate(String[] tokens, boolean trace) {
        var values = new ArrayDeque<Integer>();
        for (String token : tokens) {
            if (operator(token)) {
                if (values.size() < 2) throw new IllegalArgumentException("missing operand");
                int right = values.pop(); // Последним пришёл ПРАВЫЙ операнд.
                int left = values.pop();
                values.push(apply(token, left, right));
            } else values.push(Integer.parseInt(token)); // "-11" — число, "-" — оператор.
            if (trace) System.out.println("token=" + token + " values(top first)=" + values);
        }
        if (values.size() != 1) throw new IllegalArgumentException("not one result");
        return values.pop();
    }

    static int precedence(String op) {
        return op.equals("+") || op.equals("-") ? 1 : 2;
    }

    static String[] toPostfix(String[] tokens) {
        // Предусловие: корректное выражение, уже разбитое на числа, бинарные операторы и скобки.
        var output = new ArrayList<String>();
        var ops = new ArrayDeque<String>();
        for (String token : tokens) {
            if (token.equals("(")) ops.push(token);
            else if (token.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) output.add(ops.pop());
                if (ops.isEmpty()) throw new IllegalArgumentException("unmatched close");
                ops.pop(); // Скобка задавала границу, в вычисляемую запись не попадает.
            } else if (operator(token)) {
                while (!ops.isEmpty() && !ops.peek().equals("(") && precedence(ops.peek()) >= precedence(token)) {
                    output.add(ops.pop()); // Равный приоритет: эти бинарные операторы выполняются слева направо.
                }
                ops.push(token);
            } else {
                Integer.parseInt(token); // Проверяем формат числового токена.
                output.add(token);
            }
        }
        while (!ops.isEmpty()) {
            String op = ops.pop();
            if (op.equals("(")) throw new IllegalArgumentException("unmatched open");
            output.add(op);
        }
        return output.toArray(String[]::new);
    }

    static void showFailure(String[] tokens) {
        try { System.out.println(evaluate(tokens, false)); }
        catch (RuntimeException e) { System.out.println(Arrays.toString(tokens) + " -> " + e.getClass().getSimpleName()); }
    }

    public static void main(String[] args) {
        System.out.println("== postfix values ==");
        System.out.println("result=" + evaluate(new String[]{"8", "3", "-", "2", "*"}, true));

        System.out.println("== operand order and division ==");
        System.out.println("8 3 -=" + evaluate(new String[]{"8", "3", "-"}, false));
        System.out.println("3 8 - 2 /=" + evaluate(new String[]{"3", "8", "-", "2", "/"}, false));
        System.out.println("8 3 2 * -=" + evaluate(new String[]{"8", "3", "2", "*", "-"}, false));

        System.out.println("== precedence and parentheses ==");
        for (String text : new String[]{"8 - 3 * 2", "( 8 - 3 ) * 2", "8 - 3 - 2"}) {
            String[] postfix = toPostfix(text.split(" "));
            System.out.println(text + " -> " + Arrays.toString(postfix) + " -> " + evaluate(postfix, false));
        }

        System.out.println("== malformed input and arithmetic ==");
        showFailure(new String[]{"8", "-"});
        showFailure(new String[]{"8", "3"});
        showFailure(new String[]{"8", "0", "/"});
        showFailure(new String[]{"2147483647", "1", "+"});
        showFailure(new String[]{"-2147483648", "-1", "/"});
    }
}
