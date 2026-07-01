import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class ex10 {
    static class Lottery {
        private int[] lotteryNumbers = new int[5];

        public Lottery() {
            Random rand = new Random();
            for (int i = 0; i < 5; i++) {
                lotteryNumbers[i] = rand.nextInt(0, 10);
            }
        }

        public int digitMatch(int[] user) {
            int count = 0;
            for (int i = 0; i < 5; i++) {
                final int index = i;
                if (Arrays.stream(lotteryNumbers).anyMatch(n -> n == user[index])) {
                    count++;
                }
            }
            return count;
        }

        public int[] getLotteryNumbers() {
            return lotteryNumbers;
        }
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        Scanner sc = new Scanner(System.in);

        int[] user = new int[5];
        System.out.print("Enter 5 number: ");
        for (int i = 0; i < 5; i++) {
            user[i] = sc.nextInt();
        }
        Lottery lottery = new Lottery();
        System.out.println("Lottery number: " + Arrays.toString(lottery.getLotteryNumbers()));

        System.out.println("Match: " + lottery.digitMatch(user));
        if (lottery.digitMatch(user) == 5) {
            System.out.println("You won the grand prize");
        }
    }
}
