import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex6 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter second: ");
        int second = Integer.parseInt(br.readLine());

        if (second >= 86400) {
            System.out.println("Days: " + (double) second / 86400);
        } else if (second >= 3600) {
            System.out.println("Hours: " + (double) second / 3600);
        } else if (second >= 60) {
            System.out.println("Minutes: " + (double) second / 60);
        } else {
            System.out.println("Seconds: " + second);
        }
    }
}
