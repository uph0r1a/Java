import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex4 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number 1: ");
        int n1 = Integer.parseInt(br.readLine());

        System.out.print("Enter number 2: ");
        int n2 = Integer.parseInt(br.readLine());

        System.out.print("Enter number 3: ");
        int n3 = Integer.parseInt(br.readLine());

        System.out.println(
                (n1 + n2 > n3) && (n1 + n3 > n2) && (n2 + n3 > n1) ? "Is right triangle" : "Not right triangle");
    }
}
