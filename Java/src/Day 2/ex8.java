import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex8 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        double caloriesPerCookie = 300 / (40 / 10);

        System.out.print("Enter number of cookies: ");
        int cookies = Integer.parseInt(br.readLine());

        System.out.println("Total calories: " + cookies * caloriesPerCookie);
    }
}
