import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex8 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number of year: ");
        int year;
        while (true) {
            year = Integer.parseInt(br.readLine());
            if (year >= 1) {
                break;
            }
            System.out.println("Year cant be less than 1\nRe-enter number of year: ");
        }

        double total = 0;
        for (int i = 0; i < year; i++) {
            System.out.println("Year " + (i + 1));
            for (int j = 0; j < 12; j++) {
                System.out.print("Month " + (j + 1) + "\nEnter iches of rainfall: ");
                double rainfall;
                while (true) {
                    rainfall = Integer.parseInt(br.readLine());
                    if (rainfall >= 0) {
                        break;
                    }
                    System.out.println("Inches of rainfall cant be negative\nRe-enter inches of rainfall: ");
                }
                total += rainfall;
            }
        }
        System.out.println("Number of month: " + 12 * year + "\nTotal inches of rainfall: " + total
                + "\nAverage rainfall per month: " + total / (12 * year));
    }
}
