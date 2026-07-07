import java.util.Random;

public class ex14 {
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

    public static void main(String[] args) {
        int computerWin = 0, userWin = 0;
        for (int i = 0; i < 10; i++) {
            int computer = new Die(6).getValue(), user = new Die(6).getValue();
            if (computer > user) {
                computerWin++;
            } else if (user > computer) {
                userWin++;
            }
        }
        if (computerWin > userWin) {
            System.out.println("Computer win");
        } else if (userWin > computerWin) {
            System.out.println("User win");
        } else {
            System.out.println("Tie");
        }
    }
}
