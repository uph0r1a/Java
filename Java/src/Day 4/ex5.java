import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex5 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a string: ");
        String str = br.readLine();

        System.out.print("Enter a char: ");
        char c = br.readLine().charAt(0);

        int count = 0;

        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        System.out.print("Count: " + count);
    }
}
