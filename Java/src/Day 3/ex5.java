import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex5 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter mass: ");
        double mass = Double.parseDouble(br.readLine());
        double weight = mass * 9.8;

        System.out.println("Weight: " + weight);

        if (weight > 1000) {
            System.out.print("Too heavy");
        } else if (weight < 10) {
            System.out.println("Too light");
        }
    }
}
