import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter your name: ");
        String name = scanner.nextLine();

        System.out.print("Enter your age: ");
        int age = scanner.nextInt();

        System.out.print("Enter your height: ");
        float height = scanner.nextFloat();
        scanner.nextLine();

        System.out.print("Enter your favourite subject: ");
        String subject = scanner.nextLine();

        System.out.println("Hello! My name is " + name + ".\nI am " + age + " years old and " + height
                + " cm tall. \nMy favorite subject is " + subject + ".");
    }
}