import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class ex6 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enetr file name: ");
        String fileName = br.readLine();

        System.out.print("Enter char: ");
        char c = br.readLine().charAt(0);

        try {
            String content = Files.readString(Path.of("files/" + fileName + ".txt"));
            int count = 0;

            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == c) {
                    count++;
                }
            }

            System.out.println("Count: " + count);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
