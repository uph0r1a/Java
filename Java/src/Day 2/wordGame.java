import java.util.Scanner;

public class wordGame {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter your name: ");
        String name = scanner.nextLine();

        System.out.print("Enter your age: ");
        int age = scanner.nextInt();
        scanner.nextLine();

        System.out.print("Enter your city: ");
        String city = scanner.nextLine();

        System.out.print("Enter your college: ");
        String college = scanner.nextLine();

        System.out.print("Enter your profession: ");
        String profession = scanner.nextLine();

        System.out.print("Enter your animal: ");
        String animal = scanner.nextLine();

        System.out.print("Enter your pet's name: ");
        String petName = scanner.nextLine();

        System.out.printf(
                "There once was a person named %s who lived in %s. At the age of %d, %s went to college at %s. %s graduated and went to work as a %s. Then, %s adopted a(n) %s named %s. They both lived happily ever after!",
                name, city, age, name, college, name, profession, name, animal, petName);
    }
}
