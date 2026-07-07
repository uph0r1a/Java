import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex1 {
    public static char showChar(String str, int pos) {
        return str.charAt(pos);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a string: ");
        String str = br.readLine();

        System.out.print("Enter a index: ");
        int pos;
        while (true) {
            pos = Integer.parseInt(br.readLine());
            if (pos >= 0) {
                break;
            }
            System.out.println("Index cant be negative\nRe-enter a index: ");
        }
        System.out.println(showChar(str, pos));
    }
}
