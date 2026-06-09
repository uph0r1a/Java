import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class restaurantBill {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the charge: ");
        double charge = Double.parseDouble(br.readLine());

        System.out.printf("Meal charge: %.2f%nTax amount: %.2f%nTip amount: %.2f%nTotal bill: %.2f", charge,
                charge * 0.0675,
                charge * 0.0675 * 0.2,
                charge + charge * 0.0675 + charge * 0.0675 * 0.2);
    }
}
