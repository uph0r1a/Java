import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex16 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter the number of books purchased: ");
        int book = Integer.parseInt(br.readLine());

        int point;

        if (book == 0) {
            point = 0;
        } else if (book == 1) {
            point = 5;
        } else if (book == 2) {
            point = 15;
        } else if (book == 3) {
            point = 30;
        } else {
            point = 60;
        }

        System.out.println("Point awarded: " + point);
    }
}
