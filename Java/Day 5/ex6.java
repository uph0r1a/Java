public class ex6 {
    public static double celsius(int fahrenheit) {
        return 5.0 / 9 * (fahrenheit - 32);
    }

    public static void main(String[] args) {
        for (int i = 0; i <= 20; i++) {
            System.out.printf("%.2f%n", celsius(i));
        }
    }
}
