import java.util.Scanner;

public class ex1 {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter name: ");
        String name = scanner.nextLine();

        System.out.print("Enter age: ");
        int age = scanner.nextInt();

        System.out.print("Enter annual pay: ");
        double annualPay = scanner.nextDouble();

        System.out.printf("My name is %s, my age is %d and%nI hope to earn $%.2f per year.", name, age, annualPay);
    }
}
