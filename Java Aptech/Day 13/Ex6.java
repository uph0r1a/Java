import java.util.Deque;
import java.util.ArrayDeque;

public class Ex6 {
    public static boolean isBalanced(String expression) {
        Deque<Character> stack = new ArrayDeque<>();

        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);

            if (current == '(' || current == '{' || current == '[') {
                stack.push(current);
            }

            else if (current == ')' || current == '}' || current == ']') {
                if (stack.isEmpty()) {
                    return false;
                }

                char top = stack.peek();

                if ((current == ')' && top != '(') || (current == '}' && top != '{')
                        || (current == ']' && top != '[')) {
                    return false;
                }

                stack.pop();
            }
        }

        return stack.isEmpty();
    }

    public static void main(String[] args) {
        String[] expressions = { "{[()()]}", "{[(])}", "(()", "()", "Hello (World)", "a + [b * (c + d)]" };

        for (String expression : expressions) {
            System.out.println(expression + " -> " + isBalanced(expression));
        }
    }
}
