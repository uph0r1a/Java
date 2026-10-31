import java.util.Deque;
import java.util.ArrayDeque;

public class Ex9 {
    public static boolean isPalindrome(int number) {
        if (number < 0) {
            return false;
        }

        Deque<Integer> deque = new ArrayDeque<>();
        String digits = String.valueOf(number);

        for (int i = 0; i < digits.length(); i++) {
            deque.addLast(Character.getNumericValue(digits.charAt(i)));
        }

        while (deque.size() > 1) {
            int first = deque.pollFirst(), last = deque.pollLast();

            if (first != last) {
                return false;
            }
        }

        return true;
    }

    public static void main(String[] args) {
        int[] numbers = { 12321, 12345, 1221, 123321, 7, 0, -121 };

        for (int number : numbers) {
            System.out.println(number + " -> " + isPalindrome(number));
        }
    }
}
