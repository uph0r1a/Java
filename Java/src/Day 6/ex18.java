import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Random;

public class ex18 {
    static public class Die {
        private int sides;
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

    public static void main(String[] args) {
        Random rand = new Random();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        String[] item = { "Minnow", "Perch", "Salmon", "Tuna", "Swordfish", "Kraken Relic" };
        int total = 0, choice = 1;

        while (choice == 1) {
            int catches = rand.nextInt(6);
            total += catches + 1;
            System.out.print("You got " + item[catches] + "\nScore: " + total
                    + "\nDo you wanna continue 1) Yes 2) No ?\nEnter your choice: ");

            while (true) {
                try {
                    choice = Integer.parseInt(br.readLine());
                    if (choice == 1 || choice == 2) {
                        break;
                    }
                    System.out.print("Invalid choice\nRe-enter your choice: ");
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        }
        System.out.println("Congrat you got " + total + " points");
    }
}
