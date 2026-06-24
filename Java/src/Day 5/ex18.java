import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Random;

public class ex18 {
    public static void main(String[] args) throws IOException {
        Random rand = new Random();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        int correct = 0;
        String[] words = { "Red", "Green", "Blue", "Orange", "Yellow" };

        for (int i = 0; i < 10; i++) {
            String computer = words[rand.nextInt(0, words.length)];

            System.out.print("Enter your guess: ");
            String user;
            while (true) {
                user = br.readLine();
                if (Arrays.stream(words).anyMatch(user::equalsIgnoreCase)) {
                    break;
                }
                System.out.print("Invalid choice\nRe-enter your guess: ");
            }

            if (computer.equalsIgnoreCase(user)) {
                correct++;
            }
        }
        System.out.println("Correct: " + correct);
    }
}
