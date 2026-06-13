import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex9 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter package weight: ");
        double weight = Double.parseDouble(br.readLine());
        double charge;
        if (weight > 10) {
            charge = 3.8;
        } else if (weight > 6) {
            charge = 3.7;
        } else if (weight > 2) {
            charge = 2.2;
        } else {
            charge = 1.1;
        }
        System.out.println("Shipping charge: " + charge);
    }
}
