import java.util.Objects;
import java.util.Random;

public class ex16_17 {
    static Random rand = new Random();

    static class Coin {
        private String sideUp;

        public Coin() {
            toss();
        }

        public void toss() {
            sideUp = (rand.nextInt(2) == 1 ? "Heads" : "Tails");
        }

        public String getSideUp() {
            return sideUp;
        }
    }

    public static void main(String[] args) {
        Coin coin = new Coin();
        int heads = 0, tails = 0;

        System.out.println("Side up: " + coin.getSideUp());

        for (int i = 0; i < 20; i++) {
            coin.toss();
            System.out.println("Side up: " + coin.getSideUp());

            if (Objects.equals(coin.getSideUp(), "Heads")) {
                heads++;
            } else {
                tails++;
            }
        }

        System.out.println("Heads: " + heads + "\nTails: " + tails);

        double total = 0;

        while (total < 1) {
            Coin quarter = new Coin();
            Coin dime = new Coin();
            Coin nickel = new Coin();
            if (quarter.getSideUp().equals("Heads")) {
                total += 0.25;
            }
            if (dime.getSideUp().equals("Heads")) {
                total += 0.1;
            }
            if (nickel.getSideUp().equals("Heads")) {
                total += 0.05;
            }
        }
        System.out.println("Total: " + total);
        if (total == 1) {
            System.out.println("You win");
        } else {
            System.out.println("You lost");
        }
    }
}
