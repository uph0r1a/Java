import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class ex10 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter file name: ");
        String fileName = br.readLine();
        int sum = 0;
        try {
            for (String line : Files.readAllLines(Path.of("files/" + fileName + ".txt"))) {
                String[] words = line.split("\\s+");
                sum += words.length;
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println("Number of words: " + sum);
    }
}
