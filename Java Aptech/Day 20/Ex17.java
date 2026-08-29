import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Ex17 {
    public static void main(String[] args) {
        try {
            String content = Files.readString(Path.of("files/dulieu.txt"));
            System.out.print(content);
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}