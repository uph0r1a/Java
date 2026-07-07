import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

public class ex17_18 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Random r = new Random();

        int rand = r.nextInt(100);
        int count = 0;

        while (true) {
            System.out.print("Guess the number (0-100): ");
            int guess = Integer.parseInt(br.readLine());
            count++;

            if (guess == rand) {
                System.out.println("Correct");
                break;
            } else if (guess > rand) {
                System.out.println("Too high, try again");
            } else {
                System.out.println("Too low, try again");
            }
        }
        System.out.println("Number of guesses: " + count);
    }
}
