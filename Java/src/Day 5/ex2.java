import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex2 {
    public static double calculateRetail(double wholeSaleCost, double percentage) {
        return wholeSaleCost + wholeSaleCost * (percentage / 100);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter wholesale cost: ");
        double cost = Double.parseDouble(br.readLine());

        System.out.print("Enter markup percentage: ");
        double percentage = Double.parseDouble(br.readLine());

        System.out.println("Retail price: " + calculateRetail(cost, percentage));
    }
}
