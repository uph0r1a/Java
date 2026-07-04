import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex13 {
    public static char mapCharToDigit(char c) {
        c = Character.toUpperCase(c);
        if ("ABC".indexOf(c) != -1)
            return '2';
        if ("DEF".indexOf(c) != -1)
            return '3';
        if ("GHI".indexOf(c) != -1)
            return '4';
        if ("JKL".indexOf(c) != -1)
            return '5';
        if ("MNO".indexOf(c) != -1)
            return '6';
        if ("PQRS".indexOf(c) != -1)
            return '7';
        if ("TUV".indexOf(c) != -1)
            return '8';
        if ("WXYZ".indexOf(c) != -1)
            return '9';
        return c;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a telephone number: ");
        String number;
        while (true) {
            try {
                number = br.readLine();
                if (number.matches("^[A-Za-z0-9]{3}-[A-Za-z0-9]{3}-[A-Za-z0-9]{4}$")) {
                    break;
                }
                System.out.print("Invalid format. Re-enter telephone number: ");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (char c : number.toCharArray()) {
            if (c == '-') {
                sb.append('-');
            } else {
                sb.append(mapCharToDigit(c));
            }
        }
        System.out.println(sb);
    }
}