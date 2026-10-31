import java.util.Arrays;

public class Ex3 {
    public static int tribonacci(int n, int[] memo) {
        if (n == 0) {
            return 0;
        }

        if (n == 1 || n == 2) {
            return 1;
        }

        if (memo[n] != -1) {
            return memo[n];
        }

        memo[n] = tribonacci(n - 1, memo) + tribonacci(n - 2, memo) + tribonacci(n - 3, memo);
        return memo[n];
    }

    public static void main(String[] args) {
        int n = 9;
        int[] memo = new int[n + 1];
        Arrays.fill(memo, -1);
        int result = tribonacci(n, memo);

        System.out.println("T(" + n + ") = " + result);
    }
}
