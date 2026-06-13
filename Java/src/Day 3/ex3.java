import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex3 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter weight: ");
        double weight = Double.parseDouble(br.readLine());

        System.out.print("Enter height: ");
        double height = Double.parseDouble(br.readLine());

        double BMI = weight * 703 / Math.pow(height, 2);

        if (BMI > 25) {
            System.out.println("Overweight");
        } else if (BMI > 18.5) {
            System.out.println("Optimal");
        } else {
            System.out.println("Underweight");
        }
    }
}
