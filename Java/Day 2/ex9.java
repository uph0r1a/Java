import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex9 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the number of mile driven: ");
        double numberOfMiles = Double.parseDouble(br.readLine());

        System.out.print("Enter the gallon of gas used: ");
        double gallonOfGas = Double.parseDouble(br.readLine());

        System.out.println("Miles per gallon: " + (numberOfMiles / gallonOfGas));
    }
}
