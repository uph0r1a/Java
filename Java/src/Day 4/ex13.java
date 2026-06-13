import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class ex13 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter file name: ");
        String fileName = br.readLine();
        int count = 0;

        try {
            for (String line : Files.readAllLines(Path.of("files/" + fileName + ".txt"))) {
                if (count == 5) {
                    break;
                }

                System.out.println(line);
                count++;
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
