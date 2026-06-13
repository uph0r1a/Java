import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex13_14 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter package purchased (A, B, or C): ");
        String packagePurchased = br.readLine().toUpperCase();

        System.out.print("Enter number of hours used: ");
        int hoursUsed = Integer.parseInt(br.readLine());

        double charge;

        switch (packagePurchased) {
            case "A":
                charge = 9.95;
                if (hoursUsed > 10) {
                    charge += (hoursUsed - 10) * 2.0;
                }

                System.out.printf("Total charge: $%.2f%n", charge);

                double costB = 13.95;
                if (hoursUsed > 20) {
                    costB += (hoursUsed - 20);
                }

                double saveB = charge - costB;
                double saveC = charge - 19.95;

                if (saveB > 0) {
                    System.out.printf("You would save $%.2f by purchasing Package B.%n", saveB);
                }

                if (saveC > 0) {
                    System.out.printf("You would save $%.2f by purchasing Package C.%n", saveC);
                }
                break;

            case "B":
                charge = 13.95;
                if (hoursUsed > 20) {
                    charge += (hoursUsed - 20);
                }

                System.out.printf("Total charge: $%.2f%n", charge);

                double saveC_B = charge - 19.95;
                if (saveC_B > 0) {
                    System.out.printf("You would save $%.2f by purchasing Package C.%n", saveC_B);
                }
                break;

            case "C":
                charge = 19.95;
                System.out.printf("Total charge: $%.2f%n", charge);
                break;

            default:
                System.out.println("Invalid package.");
        }
    }
}