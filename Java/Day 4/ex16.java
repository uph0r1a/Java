import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex16 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the amount you has budgeted: ");
        double budgeted = Double.parseDouble(br.readLine());
        double total = 0;

        while (true) {
            System.out.print("Enter your expense: ");
            double expense = Double.parseDouble(br.readLine());
            total += expense;

            int choice;
            while (true) {
                System.out.print("Do you want to continue? 1)Yes 2)No: ");
                choice = Integer.parseInt(br.readLine());

                if (choice == 1 || choice == 2) {
                    break;
                }
                System.out.println("Invalid choice");
            }

            if (choice == 2) {
                break;
            }
        }
        if (total > budgeted) {
            System.out.println("Over budget: " + (total - budgeted));
        } else {
            System.out.println("Under budget: " + (budgeted - total));
        }
    }
}
