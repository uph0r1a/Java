package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ImageStorageTest {

    private String realUserHome;
    private String realDataDir;
    private Path fakeHome;

    @Before
    public void useAThrowawayHomeDirectory() throws IOException {
        // ImageStorage asks DataPaths for its folder (read on every call), so this points it at a
        // temp directory rather than touching the real data/images folder.
        realUserHome = System.getProperty("user.home");
        realDataDir = System.getProperty("lunavera.dataDir");
        fakeHome = Files.createTempDirectory("lunavera-image-test");
        System.setProperty("user.home", fakeHome.toString());
        System.setProperty("lunavera.dataDir", fakeHome.resolve("data").toString());
    }

    @After
    public void restoreRealHomeDirectory() throws IOException {
        System.setProperty("user.home", realUserHome);
        if (realDataDir == null) {
            System.clearProperty("lunavera.dataDir");
        } else {
            System.setProperty("lunavera.dataDir", realDataDir);
        }
        try (var walk = Files.walk(fakeHome)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void storedFileCanBeResolvedBackToItself() throws IOException {
        File source = Files.createTempFile("source", ".png").toFile();
        Files.writeString(source.toPath(), "fake image bytes");

        String stored = ImageStorage.storeNewFile(source);

        // What gets stored on the product is a bare file name, not a path (TODO.md item 10a) -
        // so a database copied to another machine/user still finds its images.
        assertFalse("stored value must not be an absolute path", new File(stored).isAbsolute());
        File resolved = ImageStorage.resolve(stored);
        assertNotNull(resolved);
        assertTrue(resolved.exists());
        assertEquals("fake image bytes", Files.readString(resolved.toPath()));
    }

    @Test
    public void oldAbsolutePathFromBeforeTheFixStillResolves() throws IOException {
        // Simulates a database written before this session's change, where the full path was
        // stored directly - upgrading must not orphan anyone's already-saved pictures.
        File legacy = Files.createTempFile("legacy", ".png").toFile();
        File resolved = ImageStorage.resolve(legacy.getAbsolutePath());
        assertNotNull(resolved);
        assertEquals(legacy.getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    public void missingFileResolvesToNullRatherThanAFileThatDoesNotExist() {
        assertNull(ImageStorage.resolve("this-file-was-never-saved.png"));
        assertNull(ImageStorage.resolve("/no/such/absolute/path.png"));
    }

    @Test
    public void blankOrNullNeverResolves() {
        assertNull(ImageStorage.resolve(null));
        assertNull(ImageStorage.resolve(""));
        assertNull(ImageStorage.resolve("   "));
    }

    @Test
    public void deleteQuietlyActuallyDeletesAResolvableFile() throws IOException {
        File source = Files.createTempFile("todelete", ".png").toFile();
        String stored = ImageStorage.storeNewFile(source);
        File saved = ImageStorage.resolve(stored);
        assertTrue(saved.exists());

        ImageStorage.deleteQuietly(stored);

        assertNull(ImageStorage.resolve(stored));
    }

    @Test
    public void deleteQuietlyNeverThrowsOnUnresolvableInput() {
        // Must be safe to call unconditionally after a product delete, even if the product
        // never had a picture or its file is already gone.
        ImageStorage.deleteQuietly(null);
        ImageStorage.deleteQuietly("");
        ImageStorage.deleteQuietly("never-existed.png");
    }

    private File tempImage(String suffix, String content) throws IOException {
        File source = Files.createTempFile("source", suffix).toFile();
        Files.writeString(source.toPath(), content);
        return source;
    }

    @Test
    public void twoUploadsOfTheSameFileGetTwoDifferentNamesAndTwoFiles() throws IOException {
        File source = tempImage(".png", "same bytes");

        String first = ImageStorage.storeNewFile(source);
        String second = ImageStorage.storeNewFile(source);

        assertFalse("names must differ", first.equals(second));
        assertTrue(ImageStorage.resolve(first).exists());
        assertTrue(ImageStorage.resolve(second).exists());
    }

    @Test
    public void storedNameDoesNotContainTheOriginalFileName() throws IOException {
        // Spaces, brackets, Vietnamese letters and a very long name. A name the machine's file
        // system cannot even create (a non-UTF-8 test machine) is skipped, not failed.
        String[] originals = {
                "Ca phe sua da - anh (1).JPG",
                "Cà phê sữa đá - ảnh.JPG",
                "x".repeat(150) + ".png"};
        int tried = 0;
        for (String original : originals) {
            File source;
            try {
                source = new File(Files.createTempDirectory("orig").toFile(), original);
                Files.writeString(source.toPath(), "x");
            } catch (java.nio.file.InvalidPathException | IOException unsupportedHere) {
                continue;
            }
            tried++;
            String stored = ImageStorage.storeNewFile(source);

            String baseName = original.substring(0, original.lastIndexOf('.'));
            assertFalse("original name leaked into " + stored, stored.contains(baseName));
            assertTrue("only safe characters: " + stored, stored.matches("[0-9a-f-]+\\.(png|jpg|jpeg)"));
            assertTrue("lowercase extension: " + stored, stored.endsWith(original.endsWith(".png") ? ".png" : ".jpg"));
        }
        assertTrue("at least the plain ASCII name must have been tested", tried >= 1);
    }

    @Test
    public void onlyPictureTypesAreAccepted() throws IOException {
        for (String suffix : new String[]{".png", ".PNG", ".jpg", ".jpeg", ".JpEg", ".jfif", ".gif", ".bmp"}) {
            assertNotNull(ImageStorage.resolve(ImageStorage.storeNewFile(tempImage(suffix, "x"))));
        }
        for (String suffix : new String[]{".exe", ".webp", ".txt", ""}) {
            try {
                ImageStorage.storeNewFile(tempImage(suffix, "x"));
                org.junit.Assert.fail("should have rejected " + suffix);
            } catch (IOException expected) {
                // rejected before anything was copied
            }
        }
        File noExtension = new File(Files.createTempDirectory("noext").toFile(), "picture.");
        Files.writeString(noExtension.toPath(), "x");
        try {
            ImageStorage.storeNewFile(noExtension);
            org.junit.Assert.fail("a trailing dot is not an extension");
        } catch (IOException expected) {
            // fine
        }
    }

    @Test
    public void aNameCollisionNeverOverwritesAndRetriesWithANewName() throws IOException {
        Path existing = ImageStorage.directory().resolve("taken.png");
        Files.writeString(existing, "someone else's picture");
        File source = tempImage(".png", "new picture");
        java.util.Iterator<String> names = java.util.List.of("taken", "taken", "free").iterator();

        String stored = ImageStorage.storeNewFile(source, names::next);

        assertEquals("free.png", stored);
        assertEquals("the existing file must be untouched", "someone else's picture", Files.readString(existing));
        assertEquals("new picture", Files.readString(ImageStorage.resolve(stored).toPath()));
    }

    @Test(expected = IOException.class)
    public void givesUpWhenEveryGeneratedNameIsTaken() throws IOException {
        Files.writeString(ImageStorage.directory().resolve("busy.png"), "x");
        ImageStorage.storeNewFile(tempImage(".png", "y"), () -> "busy");
    }

    @Test
    public void oldStyleStoredNamesStillResolve() throws IOException {
        // A name from before this change: <millis>_<original name>. Must keep working, untouched.
        Path old = ImageStorage.directory().resolve("1728500000000_latte da.png");
        Files.writeString(old, "old picture");
        assertNotNull(ImageStorage.resolve("1728500000000_latte da.png"));
    }

    @Test
    public void jfifIsStoredUnderAJpgName() throws IOException {
        String stored = ImageStorage.storeNewFile(tempImage(".jfif", "x"));
        assertTrue(stored, stored.endsWith(".jpg"));
    }

    @Test
    public void deleteQuietlyLeavesAFileOutsideTheImagesFolderAlone() throws IOException {
        // An old product row may hold a full path to somebody's own photo (TODO 2.18).
        File outside = tempImage(".png", "my holiday photo");
        ImageStorage.deleteQuietly(outside.getAbsolutePath());
        assertTrue("a file outside data/images must not be deleted", outside.exists());
    }

    // A real 1x1 PNG.
    private static final byte[] TINY_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    // (A real picture being accepted needs the JavaFX toolkit to decode it, which unit tests do not start;
    // it was checked in a real window instead. These cases are refused with or without the toolkit.)

    @Test
    public void aTextFileWithAPngNameIsRefused() throws IOException {
        assertNotNull(ImageStorage.pictureProblem(tempImage(".png", "not a picture")));
    }

    @Test
    public void anEmptyOrHugeFileIsRefused() throws IOException {
        assertNotNull(ImageStorage.pictureProblem(tempImage(".png", "")));
        File huge = Files.createTempFile("huge", ".png").toFile();
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(huge, "rw")) {
            f.setLength(ImageStorage.MAX_PICTURE_BYTES + 1);
        }
        assertNotNull(ImageStorage.pictureProblem(huge));
    }
}
