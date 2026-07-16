import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex5 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int userNumber = 1;
        String choice;

        do {
            System.out.println("===== Area Calculator =====");
            System.out.print("Enter shape (circle/square): ");
            String shape = br.readLine().trim().toLowerCase();

            double value, area = 0;

            if (shape.equals("circle")) {
                System.out.print("Enter radius: ");
                String input = br.readLine();

                if (input.isEmpty()) {
                    System.out.println("Error: Radius is required.");
                } else {
                    value = Double.parseDouble(input);
                    area = Math.PI * value * value;

                    System.out.println("\nUser Number: " + userNumber + "\nShape: Circle\nValue of radius: " + value);
                    System.out.printf("Calculated Area: %.2f\n", area);
                    userNumber++;
                }
            } else if (shape.equals("square")) {
                System.out.print("Enter side length: ");
                String input = br.readLine();

                if (input.isEmpty()) {
                    System.out.println("Error: Side length is required.");
                } else {
                    value = Double.parseDouble(input);
                    area = value * value;

                    System.out.println(
                            "\nUser Number: " + userNumber + "\nShape: Square\nValue of side length: " + value);
                    System.out.printf("Calculated Area: %.2f\n", area);
                    userNumber++;
                }

            } else {
                System.out.println("Invalid shape. Please enter 'circle' or 'square'.");
            }

            System.out.print("\nIs there another user? (yes/no): ");
            choice = br.readLine().trim().toLowerCase();
        } while (choice.equals("yes"));
    }
}