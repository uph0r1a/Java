import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Random;

public class ex17 {
    public static void main(String[] args) throws IOException {
        Random rand = new Random();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        boolean tie = true;
        String[] choice = { "Rock", "Paper", "Scissors" };

        while (tie) {
            String computer = choice[rand.nextInt(1, 4) - 1].toLowerCase();

            System.out.print("Enter your choice: ");
            String user;
            while (true) {
                user = br.readLine().toLowerCase();
                if (Arrays.stream(choice).anyMatch(user::equalsIgnoreCase)) {
                    break;
                }
                System.out.print("Invalid choice\nRe-enter your choice: ");
            }
            System.out.println("Computer choice: " + computer);

            if (user.equals(computer)) {
                System.out.println("It's a tie!");
            } else if (user.equals("rock") && computer.equals("scissors")) {
                System.out.println("Rock smashes scissors! You win!");
                tie = false;
            } else if (user.equals("paper") && computer.equals("rock")) {
                System.out.println("Paper covers rock! You win!");
                tie = false;
            } else if (user.equals("scissors") && computer.equals("paper")) {
                System.out.println("Scissors cut paper! You win!");
                tie = false;
            } else {
                System.out.println("You lose! " + computer + " beats " + user + ".");
                tie = false;
            }
        }
    }
}
