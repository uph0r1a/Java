import java.util.Deque;
import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;

public class Ex7 {

    public static int precedence(char operator) {
        if (operator == '*' || operator == '/') {
            return 2;
        }

        if (operator == '+' || operator == '-') {
            return 1;
        }

        return -1;
    }

    public static String infixToPostfix(String expression) {
        Deque<Character> stack = new ArrayDeque<>();
        List<Character> output = new ArrayList<>();

        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);

            if (Character.isWhitespace(current)) {
                continue;
            }

            if (Character.isLetterOrDigit(current)) {
                output.add(current);
            }

            else if (current == '(') {
                stack.push(current);
            }

            else if (current == ')') {
                while (!stack.isEmpty() && stack.peek() != '(') {
                    output.add(stack.pop());
                }

                if (stack.isEmpty()) {
                    throw new IllegalArgumentException("Mismatched parentheses.");
                }

                stack.pop();
            }

            else if (current == '+' || current == '-' || current == '*' || current == '/') {
                while (!stack.isEmpty() && stack.peek() != '(' && precedence(stack.peek()) >= precedence(current)) {
                    output.add(stack.pop());
                }

                stack.push(current);
            }

            else {
                throw new IllegalArgumentException("Invalid character: " + current);
            }
        }

        while (!stack.isEmpty()) {
            if (stack.peek() == '(') {
                throw new IllegalArgumentException("Mismatched parentheses.");
            }

            output.add(stack.pop());
        }

        StringBuilder result = new StringBuilder();

        for (char token : output) {
            if (result.length() > 0) {
                result.append(' ');
            }

            result.append(token);
        }

        return result.toString();
    }

    public static void main(String[] args) {
        String expression = "A + B * (C - D)";
        System.out.println("Infix:   " + expression + "\nPostfix: " + infixToPostfix(expression));
    }
}
