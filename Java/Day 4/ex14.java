import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class ex14 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter file name: ");
        String fileName = br.readLine();
        int count = 1;
        try {
            for (String line : Files.readAllLines(Path.of("files/" + fileName + ".txt"))) {
                System.out.println(count + ": " + line);
                count++;
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
