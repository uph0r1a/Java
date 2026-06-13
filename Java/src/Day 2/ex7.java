import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex7 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        double stateSaleTax = 0.04, countySaleTax = 0.02;

        System.out.print("Enter the amount of a purchase: ");
        double amount = Double.parseDouble(br.readLine());

        System.out.println("The amount of the purchase: " + amount + "\nThe state sales tax: " + amount * stateSaleTax
                + "\nThe county sales tax: " + amount * countySaleTax + "\nThe total sales tax: "
                + String.format("%.2f", (amount * countySaleTax + amount * stateSaleTax)) + "\nThe total of the sale: "
                + (amount + (amount * countySaleTax + amount * stateSaleTax)));

    }
}
