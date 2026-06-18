import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

public class ex19 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Random r = new Random();

        String[] color = { "Red", "Green", "Blue", "Orange", "Yellow" };
        int correct = 0;

        for (int i = 0; i < 10; i++) {
            String selected = color[r.nextInt(5)].toLowerCase();

            System.out.print("Enter the color: ");
            String guess = br.readLine().toLowerCase().strip();

            if (selected.equals(guess)) {
                correct++;
            }
        }
        System.out.println("Correct: " + correct);
    }
}
