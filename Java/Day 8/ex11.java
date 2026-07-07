import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ex11 {
    static class Die {
        private final int sides;
        private int value;
        private final Random rand = new Random();

        public Die(int numSides) {
            sides = numSides;
            roll();
        }

        public void roll() {
            value = rand.nextInt(sides) + 1;
        }

        public int getValue() {
            return value;
        }
    }

    static class Player {
        private final String name;
        private int points;

        public Player(String name) {
            this.name = name;
            points = 50;
        }

        public String getName() {
            return name;
        }

        public int getPoints() {
            return points;
        }

        public void playTurn(Die die) {
            die.roll();
            int roll = die.getValue();

            if (points - roll >= 1)
                points -= roll;
            else
                points += roll;

            System.out.println(name + " rolled " + roll + " --> Points: " + points);
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number of players: ");
        int n = Integer.parseInt(br.readLine());
        List<Player> players = new ArrayList<>();

        for (int i = 1; i <= n; i++) {
            players.add(new Player("Player " + i));
        }

        Die die = new Die(6);
        boolean gameOver = false;

        while (!gameOver) {
            for (Player player : players) {
                player.playTurn(die);

                if (player.getPoints() == 1) {
                    System.out.println("\n" + player.getName() + " wins!");
                    gameOver = true;
                    break;
                }
            }
        }
    }
}
