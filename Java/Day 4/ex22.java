import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

public class ex22 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Random r = new Random();

        String[] images = { "Cherries", "Oranges", "Plums", "Bells", "Melons", "Bars" };
        String[] words = new String[3];
        int choice = 1;
        double totalMoneyEnter = 0, totalMoneyWon = 0;

        while (choice == 1) {
            System.out.print("Enter the amount of money: ");
            double money;
            while (true) {
                money = Double.parseDouble(br.readLine());
                if (money >= 0) {
                    break;
                }
                System.out.print("Money cant be negative\nRe-enter the amount of money: ");
            }
            totalMoneyEnter += money;

            for (int i = 0; i < 3; i++) {
                words[i] = images[r.nextInt(6)];
                System.out.println(words[i]);
            }

            double won = 0;
            if (words[0].equals(words[1]) && words[1].equals(words[2])) {
                won = money * 3;
                System.out.println("You won $" + won);
            } else if (words[0].equals(words[1]) || words[1].equals(words[2]) || words[0].equals(words[2])) {
                won = money * 2;
                System.out.println("You won $" + won);
            } else {
                System.out.println("You won $0");
            }
            totalMoneyWon += won;

            while (true) {
                System.out.print("Do you wanna play again ? 1)Yes 2)No: ");
                choice = Integer.parseInt(br.readLine());
                if (choice == 1 || choice == 2) {
                    break;
                }
                System.out.println("Invalid choice");
            }
        }
        System.out.println("Total amount of money entered into the slot machine: " + totalMoneyEnter
                + "\nTotal amount won: " + totalMoneyWon);
    }
}
