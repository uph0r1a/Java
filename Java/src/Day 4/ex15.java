import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ex15 {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter file name 1: ");
        String fileName1 = br.readLine();

        System.out.print("Enter file name 2: ");
        String fileName2 = br.readLine();

        try {
            StringBuilder sb = new StringBuilder();

            for (String line : Files.readAllLines(Path.of("files/" + fileName1 + ".txt"))) {
                sb.append(line + "\n");
            }

            Files.writeString(Path.of("files/" + fileName2 + ".txt"), sb.toString().toUpperCase(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
