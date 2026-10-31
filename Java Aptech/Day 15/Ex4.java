import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;

public class Ex4 {
    public static int gcd(int a, int b, HashMap<String, Integer> memo) {
        if (b == 0) {
            return a;
        }

        String key = a + "," + b;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }

        int result = gcd(b, a % b, memo);
        memo.put(key, result);
        return result;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        HashMap<String, Integer> memo = new HashMap<>();

        System.out.print("Enter first number: ");
        int a = Integer.parseInt(reader.readLine());

        System.out.print("Enter second number: ");
        int b = Integer.parseInt(reader.readLine());

        a = Math.abs(a);
        b = Math.abs(b);

        int result = gcd(a, b, memo);

        System.out.println("GCD: " + result);
    }
}
