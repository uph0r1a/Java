public class ex9 {
    public static int ackermann(int m, int n) {
        if (m == 0) {
            return n + 1;
        }
        if (n == 0) {
            return ackermann(m - 1, 1);
        }
        return ackermann(m - 1, ackermann(m, n - 1));
    }

    public static void main(String[] args) {
        System.out.println(ackermann(0, 0) + "\n" + ackermann(0, 1) + "\n" + ackermann(1, 1) + "\n" + ackermann(1, 2)
                + "\n" + ackermann(1, 3) + "\n" + ackermann(2, 2) + "\n" + ackermann(3, 2));
    }
}
