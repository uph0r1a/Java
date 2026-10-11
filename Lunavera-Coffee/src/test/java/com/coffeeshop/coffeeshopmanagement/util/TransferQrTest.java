package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Finding the shop's own bank-transfer QR picture; a missing picture must stay missing, never invented. */
public class TransferQrTest {

    private Path folder;
    private String realDataDir;

    @Before
    public void makeFolder() throws IOException {
        folder = Files.createTempDirectory("lunavera-transferqr-test");
        realDataDir = System.getProperty("lunavera.dataDir");
        System.setProperty("lunavera.dataDir", folder.toString());
    }

    @After
    public void clean() throws IOException {
        if (realDataDir == null) {
            System.clearProperty("lunavera.dataDir");
        } else {
            System.setProperty("lunavera.dataDir", realDataDir);
        }
        try (var walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private Path touch(String name) throws IOException {
        return Files.writeString(folder.resolve(name), "picture");
    }

    @Test
    public void findsThePictureInTheFolder() throws IOException {
        Path file = touch("qr-transfer.png");
        assertEquals(Optional.of(file), TransferQr.findInFolder(folder));
    }

    @Test
    public void acceptsJpgAndJpegAndAnyCapitalisation() throws IOException {
        for (String name : new String[]{"qr-transfer.jpg", "qr-transfer.jpeg", "QR-Transfer.PNG", "Qr-TRANSFER.JpG"}) {
            Path dir = Files.createTempDirectory(folder, "case");
            Files.writeString(dir.resolve(name), "picture");
            assertTrue(name, TransferQr.findInFolder(dir).isPresent());
        }
    }

    @Test
    public void pngIsPreferredWhenSeveralPicturesExist() throws IOException {
        touch("qr-transfer.jpg");
        Path png = touch("qr-transfer.png");
        touch("qr-transfer.jpeg");
        assertEquals(Optional.of(png), TransferQr.findInFolder(folder));
    }

    @Test
    public void ignoresFilesThatAreNotThePicture() throws IOException {
        touch("qr-transfer.gif");
        touch("qr-transfer.png.txt");
        touch("my-qr-transfer.png");
        touch("qr-transfer");
        touch("qr.png");
        Files.createDirectory(folder.resolve("qr-transfer.png")); // a folder with that name is not a picture
        assertEquals(Optional.empty(), TransferQr.findInFolder(folder));
    }

    @Test
    public void aMissingOrNullFolderGivesNothing() {
        assertEquals(Optional.empty(), TransferQr.findInFolder(null));
        assertEquals(Optional.empty(), TransferQr.findInFolder(folder.resolve("does-not-exist")));
    }

    @Test
    public void theImageUrlPointsIntoTheDataFolderWhenThePictureIsThere() throws IOException {
        Path file = touch("qr-transfer.png");
        Optional<String> url = TransferQr.findImageUrl();
        assertTrue(url.isPresent());
        assertEquals(file.toUri().toString(), url.get());
    }

    @Test
    public void withNoPictureInTheDataFolderNothingIsMadeUp() {
        Optional<String> url = TransferQr.findImageUrl();
        // empty, or (only if someone bundles a picture with the program) a bundled resource - never the data folder
        assertFalse(url.isPresent() && url.get().startsWith(folder.toUri().toString()));
    }
}
