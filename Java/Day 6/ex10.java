import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

public class ex10 {
    static public class Die {
        private final int sides;
        private int value;

        public Die(int numSides) {
            sides = numSides;
            roll();
        }

        public void roll() {
            Random rand = new Random();
            value = rand.nextInt(sides) + 1;
        }

        public int getSides() {
            return sides;
        }

        public int getValue() {
            return value;
        }
    }

    public static void main(String[] args) throws IOException {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        int playerTotal = 0;
        int computerTotal = 0;

        while (true) {
            System.out.print("Do you want to roll? (y/n): ");
            String input = br.readLine();

            if (!input.equalsIgnoreCase("y")) {
                break;
            }

            computerTotal += new Die(6).getValue() + new Die(6).getValue();
            int playerRoll = new Die(6).getValue() + new Die(6).getValue();
            playerTotal += playerRoll;

            System.out.println("You rolled: " + playerRoll);
            System.out.println("Your total: " + playerTotal);

            if (playerTotal > 21) {
                System.out.println("You busted!");
                break;
            }
        }

        System.out.println("\n--- Game Over ---");
        System.out.println("Your total: " + playerTotal);
        System.out.println("Computer total: " + computerTotal);

        if (playerTotal > 21) {
            System.out.println("Computer wins!");
        } else if (computerTotal > 21) {
            System.out.println("You win!");
        } else if (playerTotal > computerTotal) {
            System.out.println("You win!");
        } else if (playerTotal < computerTotal) {
            System.out.println("Computer wins!");
        } else {
            System.out.println("Tie!");
        }
    }
}
