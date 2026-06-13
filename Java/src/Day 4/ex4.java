import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex4 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter day: ");
        int day = Integer.parseInt(br.readLine());

        System.out.printf("%-5s %s%n%s\n", "Day", "Salary", "-".repeat(12));
        for (int i = 1; i <= day; i++) {
            System.out.printf("%-5s %s%n", i, Math.pow(2, i - 1));
        }
    }
}
