import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex20 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter length: ");
        int length = Integer.parseInt(br.readLine());

        System.out.println(("X".repeat(length) + "\n").repeat(length));
    }
}
