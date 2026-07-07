import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex10 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;

        while (true) {
            System.out.print("Enter number: ");
            int num = Integer.parseInt(br.readLine());

            if (num == -99) {
                break;
            }

            if (num < min) {
                min = num;
            } else if (num > max) {
                max = num;
            }
        }

        System.out.println("Max: " + max + "\nMin: " + min);
    }
}
