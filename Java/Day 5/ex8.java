import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex8 {
    public static void showKilometers(double meters) {
        System.out.println(meters + " meters is " + meters * 0.001 + " kilometers");
    }

    public static void showFeet(double meters) {
        System.out.println(meters + " meters is " + meters * 3.281 + " feet");
    }

    public static void showInches(double meters) {
        System.out.println(meters + " meters is " + meters * 39.37 + " inches");
    }

    public static void menu() {
        System.out.print(
                "\n1.  Convert to kilometers\n2.  Convert to inches\n3.  Convert to feet\n4.  Quit the program\n\nEnter your choice: ");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter distance in meters: ");
        double meters;
        while (true) {
            meters = Double.parseDouble(br.readLine());
            if (meters >= 0) {
                break;
            }
            System.out.println("Distance cant be negative\nRe-enter distance in meters");
        }

        boolean isExit = false;
        while (!isExit) {
            menu();
            int choice = Integer.parseInt(br.readLine());

            switch (choice) {
                case 1:
                    showKilometers(meters);
                    break;
                case 2:
                    showInches(meters);
                    break;
                case 3:
                    showFeet(meters);
                    break;
                case 4:
                    isExit = true;
                    System.out.println("Bye!");
                    break;
                default:
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                    break;
            }
        }
    }
}
