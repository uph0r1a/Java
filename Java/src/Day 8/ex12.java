import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ex12 {
    static Random rand = new Random();
    static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    static class Coin {
        private String sideUp;

        public Coin() {
            toss();
        }

        public void toss() {
            sideUp = (rand.nextInt(2) == 1) ? "Heads" : "Tails";
        }

        public String getSideUp() {
            return sideUp;
        }
    }

    static class Player {
        private String name;
        private int points;

        public Player(String name) {
            this.name = name;
            points = 0;
        }

        public String getName() {
            return name;
        }

        public int getPoints() {
            return points;
        }

        public void takeTurn(Coin coin) throws IOException {
            System.out.print(name + ", guess (Heads/Tails): ");
            String guess = br.readLine();

            coin.toss();

            System.out.println("Coin landed on: " + coin.getSideUp());

            if (guess.equalsIgnoreCase(coin.getSideUp())) {
                points++;
                System.out.println("Correct! +1 point");
            } else {
                points--;
                System.out.println("Wrong! -1 point");
            }

            System.out.println(name + "'s score: " + points);
            System.out.println();
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        System.out.print("Enter number of players: ");
        int n = Integer.parseInt(br.readLine());
        List<Player> players = new ArrayList<>();

        for (int i = 1; i <= n; i++) {
            players.add(new Player("Player " + i));
        }

        Coin coin = new Coin();
        boolean gameOver = false;
        while (!gameOver) {
            for (Player player : players) {
                player.takeTurn(coin);

                if (player.getPoints() >= 5) {
                    System.out.println(player.getName() + " wins!");
                    gameOver = true;
                    break;
                }
            }
        }
    }
}
