import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your weight: ");
        float weight = scanner.nextFloat();

        System.out.print("Enter your height: ");
        float height = scanner.nextFloat();

        float BMI = weight / (height * height);

        System.out.println("Your BMI is: " + BMI);

        if (BMI >= 40) {
            System.out.print("Obesity (Class 3 / Severe)");
        } else if (BMI >= 35) {
            System.out.print("Obesity (Class 2)");
        } else if (BMI >= 30) {
            System.out.print("Obesity (Class 1)");
        } else if (BMI >= 25) {
            System.out.print("Overweight");
        } else if (BMI >= 18.5) {
            System.out.print("Healthy Weight");
        } else {
            System.out.print("Underweight");
        }
    }
}