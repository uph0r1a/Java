import java.io.BufferedReader;
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

    public static void main(String[] args) {
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
        System.out.println("Sum: " + sum(n));
    }
}
