import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ex15 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();

        System.out.print("Enter a sentence: ");
        String str = br.readLine().toUpperCase();
        String[] words = str.trim().split("\\s+");

        for (String word : words) {
            if (word.length() == 1) {
                sb.append(word).append("AY");
            } else {
                String pig = word.substring(1) + word.charAt(0) + "AY";
                sb.append(pig);
            }
            sb.append(" ");
        }
        System.out.println("Pig Latin: " + sb.toString().trim());
    }
}