import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * TODO 6.44: prints the file-by-file list of the starter photos as a Markdown table (name, size, SHA-256, and whether the file carries
 * AI content credentials), for docs/PHOTO_INVENTORY.md. Run from the project folder with the JDK alone:
 * {@code java tools/PhotoInventory.java > docs/PHOTO_INVENTORY.md.new} and read it before replacing the file; the column "Where it
 * came from" is for the owner and is not made by this tool. {@code PhotoInventoryTest} fails when the list in the document no longer
 * matches the files.
 */
public class PhotoInventory {
    public static void main(String[] args) throws IOException {
        Path folder = Path.of("src/main/resources/seed-menu");
        List<Path> files;
        try (Stream<Path> list = Files.list(folder)) {
            files = list.filter(Files::isRegularFile).sorted().toList();
        }
        System.out.println("| File | Bytes | SHA-256 (first 16) | AI content credentials | Where it came from (owner) |");
        System.out.println("|---|---|---|---|---|");
        for (Path file : files) {
            byte[] bytes = Files.readAllBytes(file);
            String sha = HexFormat.of().formatHex(sha256(bytes)).substring(0, 16);
            System.out.println("| `" + file.getFileName() + "` | " + bytes.length + " | " + sha + " | " + (carriesAiCredentials(bytes) ? "yes" : "no") + " | *(to be written)* |");
        }
    }

    /** The marks of Google's content credentials: C2PA manifest, and the words for AI-made media or the SynthID watermark. */
    static boolean carriesAiCredentials(byte[] bytes) {
        String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        return text.contains("c2pa") && (text.contains("trainedAlgorithmicMedia") || text.contains("SynthID"));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
