import java.util.Scanner;

public class ex17 {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        double sugar = 1.5, butter = 1, flour = 2.75, cookies = 48;
        System.out.print("Enter the amount of cookies: ");
        int cookieAmount = scanner.nextInt();

        System.out.printf("%.2f cups of sugar%n%.2f cups of butter%n%.2f cups of flour",
                (cookieAmount * (sugar / cookies)),
                (cookieAmount * (butter / cookies)),
                (cookieAmount * (flour / cookies)));
    }
}
