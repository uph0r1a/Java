import java.util.Random;

public class ex21 {
    public static void main(String[] args) {
        Random r = new Random();
        int playerWin = 0, computerWin = 0;
        for (int i = 0; i < 10; i++) {
            int computer = r.nextInt((6 - 1) + 1) + 1, user = r.nextInt((6 - 1) + 1) + 1;

            if (computer > user) {
                computerWin++;
            } else if (user > computer) {
                playerWin++;
            }
        }
        if (computerWin > playerWin) {
            System.out.println("Computer win");
        } else if (playerWin > computerWin) {
            System.out.println("Player win");
        } else {
            System.out.println("Tie");
        }
    }
}
