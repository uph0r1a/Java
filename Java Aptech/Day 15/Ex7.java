import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex7 {
    public static int sum(int n) {
        if (n == 0) {
            return 0;
        }
        return n + sum(n - 1);
    }

    public static int sumLoop(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        return sum;
    }

    private static String buildSequence(int n) {
        if (n <= 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            sb.append(i);
            if (i < n) {
                sb.append(" + ");
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter n: ");
        int n;
        while (true) {
            try {
                n = Integer.parseInt(br.readLine());
                if (n >= 0) {
                    break;
                }
                System.out.print("Invalid n\nRe-enter n: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        int recursiveResult = sum(n), loopResult = sumLoop(n + 1);
        System.out.println(buildSequence(n) + " = " + recursiveResult + "\nSum (recursive): " + recursiveResult
                + "\nSum (iterative): " + loopResult);
    }
}