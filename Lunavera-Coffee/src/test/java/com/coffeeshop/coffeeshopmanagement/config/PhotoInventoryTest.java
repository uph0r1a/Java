package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.44: what the README and {@code docs/PHOTO_INVENTORY.md} say about the starter photos is checked against the files. Only the owner
 * can say who made the photos; this keeps the facts the program can check from drifting: the number and kinds of files, the three photos
 * with AI content credentials (and only those), the logo and background without metadata, and the checksum list. Source files only.
 */
public class PhotoInventoryTest {

    private static final Path SEED = Paths.get("src/main/resources/seed-menu");
    private static final List<String> AI_PHOTOS = List.of("nuoc-ep-cam.jfif", "nuoc-ep-tao.jfif", "tra-dao.jfif");

    private static String text(String file) throws IOException {
        return new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
    }

    private static List<Path> photos() throws IOException {
        try (Stream<Path> list = Files.list(SEED)) {
            return list.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static boolean carriesAiCredentials(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.ISO_8859_1);
        return s.contains("c2pa") && (s.contains("trainedAlgorithmicMedia") || s.contains("SynthID"));
    }

    private static boolean namesAnOrigin(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String mark : new String[]{"dc:creator", "xmpRights", "Copyright", "photoshop:", "<xmp:CreatorTool"}) if (s.contains(mark)) return true;
        return false;
    }

    private static String sha16(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    @Test
    public void thereAreThirtyThreeFilesOfTheKindsTheReadmeSays() throws IOException {
        int jfif = 0, jpg = 0, png = 0;
        for (Path p : photos()) {
            String n = p.getFileName().toString().toLowerCase();
            if (n.endsWith(".jfif")) jfif++; else if (n.endsWith(".jpg")) jpg++; else if (n.endsWith(".png")) png++;
        }
        assertEquals(33, photos().size());
        assertEquals(26, jfif);
        assertEquals(6, jpg);
        assertEquals(1, png);
        String readme = text("README.md");
        assertTrue("README: 33 files, 26 jfif, 6 jpg, 1 png", readme.contains("33 files: 26 `.jfif`, 6 `.jpg`, 1 `.png`"));
    }

    @Test
    public void exactlyTheThreeNamedPhotosCarryAiCredentialsAndNoOtherFileNamesAnOrigin() throws IOException {
        List<String> withCredentials = new ArrayList<>();
        for (Path p : photos()) {
            byte[] bytes = Files.readAllBytes(p);
            if (carriesAiCredentials(bytes)) withCredentials.add(p.getFileName().toString());
            else assertFalse(p.getFileName() + " names an author or a licence: put it in the README", namesAnOrigin(bytes));
        }
        assertEquals(AI_PHOTOS, withCredentials);
        String readme = text("README.md");
        for (String name : AI_PHOTOS) assertTrue("README names " + name, readme.contains(name));
    }

    @Test
    public void theLogoAndTheLoginBackgroundAreBareImagesWithNoMetadata() throws IOException {
        for (String file : new String[]{"src/main/resources/images/LunaveraLogo.png", "src/main/resources/images/LoginBackground.png"}) {
            byte[] b = Files.readAllBytes(Paths.get(file));
            int i = 8;
            TreeSet<String> kinds = new TreeSet<>();
            while (i + 8 <= b.length) {
                long length = ((b[i] & 0xFFL) << 24) | ((b[i + 1] & 0xFFL) << 16) | ((b[i + 2] & 0xFFL) << 8) | (b[i + 3] & 0xFFL);
                kinds.add(new String(b, i + 4, 4, StandardCharsets.ISO_8859_1));
                i += (int) (12 + length);
            }
            for (String metadata : new String[]{"tEXt", "zTXt", "iTXt", "eXIf"}) assertFalse(file + " has a " + metadata + " chunk", kinds.contains(metadata));
        }
    }

    @Test
    public void theInventoryPageListsEveryFileOnceWithItsCurrentChecksumAndAiMark() throws IOException {
        String doc = text("docs/PHOTO_INVENTORY.md");
        Map<String, String[]> rows = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(?m)^\\| `([^`]+)` \\| (\\d+) \\| ([0-9a-f]{16}) \\| (yes|no) \\|").matcher(doc);
        while (m.find()) assertNull("listed twice: " + m.group(1), rows.put(m.group(1), new String[]{m.group(2), m.group(3), m.group(4)}));
        List<String> unlisted = new ArrayList<>();
        for (Path p : photos()) {
            String name = p.getFileName().toString();
            byte[] bytes = Files.readAllBytes(p);
            String[] row = rows.remove(name);
            if (row == null) { unlisted.add(name); continue; }
            assertEquals(name + ": bytes", String.valueOf(bytes.length), row[0]);
            assertEquals(name + ": checksum (the file was replaced: say where the new one came from)", sha16(bytes), row[1]);
            assertEquals(name + ": AI mark", carriesAiCredentials(bytes) ? "yes" : "no", row[2]);
        }
        assertTrue("photos missing from docs/PHOTO_INVENTORY.md (run java tools/PhotoInventory.java): " + unlisted, unlisted.isEmpty());
        assertTrue("lines in docs/PHOTO_INVENTORY.md for files that are gone: " + rows.keySet(), rows.isEmpty());
        for (String name : AI_PHOTOS) assertTrue(doc.contains(name));
    }
}
