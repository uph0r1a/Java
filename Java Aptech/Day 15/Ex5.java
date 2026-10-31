import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;

public class Ex5 {
    public static int fibonacci(int n, HashMap<Integer, Integer> memo) {
        if (n == 0) {
            return 0;
        }

        if (n == 1) {
            return 1;
        }

        if (memo.containsKey(n)) {
            return memo.get(n);
        }

        int result = fibonacci(n - 1, memo) + fibonacci(n - 2, memo);
        memo.put(n, result);
        return result;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        HashMap<Integer, Integer> memo = new HashMap<>();

        System.out.print("Enter n: ");
        int n = Integer.parseInt(reader.readLine());

        if (n < 0 || n > 46) {
            System.out.println("Please enter a number between 0 and 46.");
            return;
        }

        int result = fibonacci(n, memo);

        System.out.println("Fibonacci number F(" + n + ") = " + result);
    }
}
