import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex2 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter month: ");
        int month = Integer.parseInt(br.readLine());

        System.out.print("Enter day: ");
        int day = Integer.parseInt(br.readLine());

        System.out.print("Enter year: ");
        int year = Integer.parseInt(br.readLine());

        if (month * day == year) {
            System.out.println("Magic Date");
        } else {
            System.out.println("Not magic date");
        }
    }
}
