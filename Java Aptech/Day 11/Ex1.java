import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex1 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter amount of pizza: ");
        int amount = Integer.parseInt(br.readLine());

        System.out.print("Enter shipping time: ");
        int time = Integer.parseInt(br.readLine());

        double price;
        if (time > 30) {
            price = 0;
        } else if (time >= 20) {
            price = 2.5;
        } else if (time >= 10) {
            price = 4;
        } else {
            price = 5.5;
        }

        System.out.println("Total price: $" + (price * amount));
    }
}