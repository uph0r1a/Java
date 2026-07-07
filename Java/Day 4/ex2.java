import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex2 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the speed: ");
        double speed;
        while (true) {
            speed = Double.parseDouble(br.readLine());
            if (speed >= 0) {
                break;
            }
            System.out.print("Speed cannot be negative\nRe-enter the speed: ");
        }

        System.out.print("Enter the number of hour traveled: ");
        int hour;
        while (true) {
            hour = Integer.parseInt(br.readLine());
            if (hour >= 1) {
                break;
            }
            System.out.print("Time cant be less than 1\nRe-enter time: ");
        }

        System.out.printf("%-5s %s%n%s%n", "Hour", "Distance Traveled", "-".repeat(23));
        for (int i = 1; i <= hour; i++) {
            System.out.printf("%-10d %.2f%n", i, speed * i);
        }
    }
}
