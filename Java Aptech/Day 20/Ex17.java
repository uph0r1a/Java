import java.nio.file.Files;
import java.nio.file.Path;

public class Ex17 {
    public static void main(String[] args) {
        try {
            for (String line : Files.readAllLines(Path.of("files/dulieu.txt"))) {
                System.out.println(line);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
