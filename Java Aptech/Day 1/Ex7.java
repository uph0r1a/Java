import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex7 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter deposit amount: ");
        double deposit;
        while (true) {
            try {
                deposit = Double.parseDouble(br.readLine());
                if (deposit > 0) {
                    break;
                }
                System.out.print("Invalid deposit\nRe-enter deposit: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        double interest;
        if (deposit >= 7000) {
            interest = 5;
        } else if (deposit >= 2000) {
            interest = 4.5;
        } else {
            interest = 4;
        }
        System.out.println("Interest: " + interest);
    }
}
