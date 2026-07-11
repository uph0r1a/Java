import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Ex2 {
    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter number: ");
        int n = Integer.parseInt(br.readLine());

        System.out.println(n >= 0 ? "This is a positive interger" : "This is a negative interger");
    }
}