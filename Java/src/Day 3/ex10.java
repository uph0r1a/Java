import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex10 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the number of calories: ");
        double calories = Double.parseDouble(br.readLine());

        System.out.print("Enter the fat grams: ");
        double fat;
        while (true) {
            fat = Double.parseDouble(br.readLine());
            if (fat * 9 < calories) {
                break;
            }
            System.out.print(
                    "Calories from fat cannot be greater than total number of calories\nRe-enter the fat gram: ");
        }

        System.out.println("Percentage of the calories from fat: " + (fat * 9 / calories) * 100 + "%");
    }
}
