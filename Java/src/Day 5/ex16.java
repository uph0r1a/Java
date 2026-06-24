import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex16 {
    public static double presentValue(double f, double r, int n) {
        return f / Math.pow((1 + r), n);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the future value: ");
        double f = Double.parseDouble(br.readLine());

        System.out.print("Enter the annual interest rate: ");
        double r = Double.parseDouble(br.readLine());

        System.out.print("Enter the number of year: ");
        int n = Integer.parseInt(br.readLine());

        System.out.printf("Present value: %.2f", presentValue(f, r, n));
    }
}
