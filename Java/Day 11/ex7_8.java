import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ex7_8 {
    public static void encryptionFile(String fileName) {
        try (BufferedReader br = Files.newBufferedReader(Path.of("files/" + fileName + ".txt"))) {
            StringBuilder sb = new StringBuilder();
            int charCode;
            while ((charCode = br.read()) != -1) {
                sb.append((char) (charCode + 10));
            }
            Files.writeString(Path.of("files/encrypted.txt"), sb.toString(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void decryptionFile() {
        try (BufferedReader br = Files.newBufferedReader(Path.of("files/encrypted.txt"))) {
            StringBuilder sb = new StringBuilder();
            int charCode;
            while ((charCode = br.read()) != -1) {
                sb.append((char) (charCode - 10));
            }
            Files.writeString(Path.of("files/decrypted.txt"), sb.toString(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Enter file name: ");
        String fileName = br.readLine();

        encryptionFile(fileName);
        decryptionFile();
    }
}
