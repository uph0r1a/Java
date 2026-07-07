import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex9 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a series of number: ");
        String str = br.readLine();

        int max = -1, min = 10, sum = 0;
        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) {
                int num = c - '0';
                sum += num;
                if (num > max) {
                    max = num;
                }
                if (num < min) {
                    min = num;
                }
            }
        }
        System.out.println("Sum: " + sum + "\nMax: " + max + "\nMin: " + min);
    }
}
