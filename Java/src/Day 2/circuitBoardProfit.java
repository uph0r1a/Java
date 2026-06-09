import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class circuitBoardProfit {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter retail price of a circuit board: ");
        double price = Double.parseDouble(br.readLine());

        System.out.print("Profit: " + price * 0.4);
    }
}
