public class ex11 {
    public static void main(String[] args) {
        System.out.printf("%-10s%s%n%s%n", "Celsius", "Fahrenheit", "-".repeat(20));
        for (int i = 0; i <= 20; i++) {
            System.out.printf("%-10s%.2f%n", i, ((9.0 / 5) * i + 32));
        }
    }
}
