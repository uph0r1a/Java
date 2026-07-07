import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex8 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the number of packages purchased: ");
        int amount = Integer.parseInt(br.readLine());
        double discount;
        if (amount >= 100) {
            discount = 0.5;
        } else if (amount >= 50) {
            discount = 0.4;
        } else if (amount >= 20) {
            discount = 0.3;
        } else {
            discount = 0.2;
        }

        System.out.println("Amount of the discount: " + (99 * amount * discount) + "\nTotal amount: "
                + ((99 * amount) - (99 * amount * discount)));
    }
}
