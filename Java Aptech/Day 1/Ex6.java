import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Ex6 {
    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a character: ");
        String c;
        while (true) {
            try {
                c = br.readLine();
                if (c.matches("[A-Za-z]")) {
                    break;
                }
                System.out.print("Invalid character\nRe-enter character: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        if ("aeiuo".contains(c.toLowerCase())) {
            System.out.println("Is vowel");
        } else {
            System.out.println("Is consonant");
        }
    }
}
