import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex15 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number of check written: ");
        int check = Integer.parseInt(br.readLine());

        double checkFee;

        if (check >= 60) {
            checkFee = check * 0.04;
        } else if (check >= 40) {
            checkFee = check * 0.06;
        } else if (check >= 20) {
            checkFee = check * 0.08;
        } else {
            checkFee = check * 0.1;
        }

        System.out.println("Bank's service fees: " + (10 + checkFee));
    }
}
