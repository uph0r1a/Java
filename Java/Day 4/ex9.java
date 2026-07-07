import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex9 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter starting number of organisms: ");
        int startingNumber = Integer.parseInt(br.readLine());

        System.out.print("Enter average daily population increase (%): ");
        double averageIncrease = Double.parseDouble(br.readLine());

        System.out.print("Enter number of days: ");
        int days = Integer.parseInt(br.readLine());

        double population = startingNumber;

        System.out.printf("%-5s %-15s%n", "Day", "Population");
        System.out.println("--------------------");

        for (int i = 1; i <= days; i++) {
            System.out.printf("%-5d %-15.2f%n", i, population);
            population += population * (averageIncrease / 100);
        }
    }
}