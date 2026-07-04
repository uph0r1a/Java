import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex14 {
    public static void main(String[] args) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter a sentence: ");
        String str = br.readLine();

        for (int i = 0; i < str.length(); i++) {
            if (i == 0) {
                sb.append(str.charAt(i));
                continue;
            }
            if (Character.isUpperCase(str.charAt(i))) {
                sb.append(" " + Character.toLowerCase(str.charAt(i)));
                continue;
            }
            sb.append(str.charAt(i));
        }
        System.out.println(sb);
    }
}
