package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** TODO 3.73: a phone photo is turned the right way up, made smaller and stored without its metadata (GPS position, phone model, time). */
public class PictureCleanerTest {

    // ---------------------------------------------------------------- building a phone-like JPEG by hand

    /** An Exif APP1 segment with the orientation tag and a GPS block marker string; {@code little} chooses the byte order. */
    private static byte[] exifSegment(int orientation, boolean little) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        byte[] order = little ? new byte[]{'I', 'I'} : new byte[]{'M', 'M'};
        tiff.writeBytes(order);
        tiff.writeBytes(num(42, 2, little));
        tiff.writeBytes(num(8, 4, little));      // IFD0 follows at once
        tiff.writeBytes(num(2, 2, little));      // two entries
        tiff.writeBytes(num(0x010F, 2, little)); // Make
        tiff.writeBytes(num(2, 2, little));
        tiff.writeBytes(num(8, 4, little));
        tiff.writeBytes(num(38, 4, little));     // offset of the text, after the entries
        tiff.writeBytes(num(0x0112, 2, little)); // Orientation
        tiff.writeBytes(num(3, 2, little));
        tiff.writeBytes(num(1, 4, little));
        tiff.writeBytes(num(orientation, 2, little));
        tiff.writeBytes(num(0, 2, little));
        tiff.writeBytes(num(0, 4, little));      // no next IFD
        tiff.writeBytes("GPS-10.77N-106.70E".getBytes(StandardCharsets.US_ASCII)); // stands for the GPS block and the phone make
        ByteArrayOutputStream seg = new ByteArrayOutputStream();
        seg.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        seg.writeBytes(tiff.toByteArray());
        byte[] body = seg.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xE1);
        out.writeBytes(num(body.length + 2, 2, false));
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static byte[] num(long value, int bytes, boolean little) {
        byte[] b = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            int shift = 8 * (little ? i : bytes - 1 - i);
            b[i] = (byte) (value >> shift);
        }
        return b;
    }

    /** A JPEG of the picture with an Exif segment put right after the start marker, as a phone does. */
    private static byte[] phoneJpeg(BufferedImage image, int orientation, boolean little) throws IOException {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", plain);
        byte[] jpeg = plain.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.writeBytes(exifSegment(orientation, little));
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static BufferedImage solid(int w, int h, int rgb) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, y, rgb);
        return image;
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    // ---------------------------------------------------------------- reading the orientation

    @Test
    public void theOrientationIsReadInBothByteOrders() throws IOException {
        for (int o = 1; o <= 8; o++) {
            assertEquals(o, JpegMetadata.orientation(phoneJpeg(solid(8, 8, 0x336699), o, true)));
            assertEquals(o, JpegMetadata.orientation(phoneJpeg(solid(8, 8, 0x336699), o, false)));
        }
    }

    @Test
    public void noExifOrAnOddFileMeansAsItIs() throws IOException {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(solid(8, 8, 0xFF0000), "jpg", plain);
        assertEquals(1, JpegMetadata.orientation(plain.toByteArray()));
        assertEquals(1, JpegMetadata.orientation(new byte[]{1, 2, 3}));
        assertEquals(1, JpegMetadata.orientation(null));
        byte[] cut = phoneJpeg(solid(8, 8, 0), 6, true);
        assertEquals(1, JpegMetadata.orientation(java.util.Arrays.copyOf(cut, 14))); // cut off in the middle of the Exif block
        assertEquals(1, JpegMetadata.orientation(phoneJpeg(solid(8, 8, 0), 9, true))); // a value outside 1..8
    }

    // ---------------------------------------------------------------- the eight turns, checked pixel by pixel

    @Test
    public void everyOrientationPutsEveryPixelWhereTheSpecificationSays() {
        int w = 3, h = 2;
        BufferedImage src = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) src.setRGB(x, y, 0x010000 * (x + 1) + 0x000100 * (y + 1)); // each pixel its own colour
        for (int o = 1; o <= 8; o++) {
            BufferedImage out = PictureCleaner.oriented(src, o, false);
            boolean swapped = o >= 5;
            assertEquals("width of " + o, swapped ? h : w, out.getWidth());
            assertEquals("height of " + o, swapped ? w : h, out.getHeight());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int[] to = target(o, x, y, w, h);
                    assertEquals("orientation " + o + " pixel " + x + "," + y, src.getRGB(x, y) & 0xFFFFFF, out.getRGB(to[0], to[1]) & 0xFFFFFF);
                }
            }
        }
    }

    /** The Exif table, written out on its own (not the matrices of the class): where the pixel at x,y of the stored picture belongs. */
    private static int[] target(int o, int x, int y, int w, int h) {
        return switch (o) {
            case 2 -> new int[]{w - 1 - x, y};
            case 3 -> new int[]{w - 1 - x, h - 1 - y};
            case 4 -> new int[]{x, h - 1 - y};
            case 5 -> new int[]{y, x};
            case 6 -> new int[]{h - 1 - y, x};
            case 7 -> new int[]{h - 1 - y, w - 1 - x};
            case 8 -> new int[]{y, w - 1 - x};
            default -> new int[]{x, y};
        };
    }

    // ---------------------------------------------------------------- the whole clean-up

    @Test
    public void aPortraitPhoneJpegComesOutUprightWithoutItsMetadata() throws IOException {
        // stored as 40 wide x 20 high with orientation 6: the photo is really 20 wide x 40 high; the left half is red, the right half blue
        BufferedImage landscape = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 40; x++) landscape.setRGB(x, y, x < 20 ? 0xFF0000 : 0x0000FF);
        byte[] original = phoneJpeg(landscape, 6, true);
        assertTrue("the test file carries the position", contains(original, "GPS-10.77N"));

        PictureCleaner.Cleaned cleaned = PictureCleaner.clean(original, "jpg");

        assertEquals("jpg", cleaned.extension());
        assertFalse("no GPS left", contains(cleaned.bytes(), "GPS-10.77N"));
        assertFalse("no Exif block left", contains(cleaned.bytes(), "Exif"));
        assertEquals("nothing says it is turned any more", 1, JpegMetadata.orientation(cleaned.bytes()));
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(cleaned.bytes()));
        assertEquals(20, stored.getWidth());
        assertEquals(40, stored.getHeight());
        // turned a quarter clockwise: what was the left (red) half is now the top half
        assertTrue(isNear(stored.getRGB(10, 5), 0xFF0000));
        assertTrue(isNear(stored.getRGB(10, 35), 0x0000FF));
    }

    private static boolean isNear(int rgb, int expected) {
        for (int shift = 16; shift >= 0; shift -= 8) {
            if (Math.abs(((rgb >> shift) & 0xFF) - ((expected >> shift) & 0xFF)) > 40) return false;
        }
        return true;
    }

    @Test
    public void aBigPictureIsMadeSmallerAndASmallOneIsLeftAlone() throws IOException {
        assertArrayEquals(new int[]{1024, 768}, PictureCleaner.fitted(4000, 3000));
        assertArrayEquals(new int[]{768, 1024}, PictureCleaner.fitted(3000, 4000));
        assertArrayEquals(new int[]{640, 480}, PictureCleaner.fitted(640, 480));
        assertArrayEquals(new int[]{1024, 1}, PictureCleaner.fitted(8000, 8));
        ByteArrayOutputStream big = new ByteArrayOutputStream();
        ImageIO.write(solid(3000, 1500, 0x884422), "png", big);
        PictureCleaner.Cleaned cleaned = PictureCleaner.clean(big.toByteArray(), "png");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(cleaned.bytes()));
        assertEquals(1024, stored.getWidth());
        assertEquals(512, stored.getHeight());
    }

    @Test
    public void aPngKeepsItsTransparencyAndAGifBecomesAPng() throws IOException {
        BufferedImage see = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        see.setRGB(0, 0, 0x80FF0000);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(see, "png", png);
        PictureCleaner.Cleaned cleaned = PictureCleaner.clean(png.toByteArray(), "png");
        assertEquals("png", cleaned.extension());
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(cleaned.bytes()));
        assertTrue("alpha kept", stored.getColorModel().hasAlpha());
        assertEquals(0x80, stored.getRGB(0, 0) >>> 24);
        assertEquals(0, stored.getRGB(5, 5) >>> 24);

        ByteArrayOutputStream gif = new ByteArrayOutputStream();
        ImageIO.write(solid(10, 10, 0x00FF00), "gif", gif);
        assertEquals("png", PictureCleaner.clean(gif.toByteArray(), "gif").extension());
    }

    @Test
    public void somethingThatIsNotAPictureIsLeftToTheCaller() throws IOException {
        assertNull(PictureCleaner.clean("not a picture".getBytes(StandardCharsets.UTF_8), "png"));
    }

    // ---------------------------------------------------------------- the fallback: strip the segments of a JPEG that cannot be decoded

    @Test
    public void withoutMetadataRemovesTheExifAndKeepsTheRest() throws IOException {
        byte[] original = phoneJpeg(solid(16, 16, 0x336699), 6, false);
        byte[] stripped = JpegMetadata.withoutMetadata(original);
        assertFalse(contains(stripped, "GPS-10.77N"));
        assertTrue(stripped.length < original.length);
        BufferedImage still = ImageIO.read(new ByteArrayInputStream(stripped));
        assertNotNull("still a picture", still);
        assertEquals(16, still.getWidth());
        // not a JPEG: handed back unchanged
        byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(text, JpegMetadata.withoutMetadata(text));
    }

    // ---------------------------------------------------------------- through ImageStorage

    private String realDataDir;
    private Path home;

    @Before
    public void throwawayDataFolder() throws IOException {
        realDataDir = System.getProperty("lunavera.dataDir");
        home = Files.createTempDirectory("lunavera-clean-test");
        System.setProperty("lunavera.dataDir", home.resolve("data").toString());
    }

    @After
    public void restore() throws IOException {
        if (realDataDir == null) System.clearProperty("lunavera.dataDir"); else System.setProperty("lunavera.dataDir", realDataDir);
        try (var walk = Files.walk(home)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void thePickedPhotoIsStoredCleanedNotCopied() throws IOException {
        File picked = new File(home.toFile(), "IMG_0001.JPG");
        Files.write(picked.toPath(), phoneJpeg(solid(64, 32, 0x336699), 6, true));

        String stored = ImageStorage.storeNewFile(picked);

        byte[] bytes = Files.readAllBytes(ImageStorage.resolve(stored).toPath());
        assertFalse(contains(bytes, "GPS-10.77N"));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals("turned upright", 32, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(Files.readString(picked.toPath(), StandardCharsets.ISO_8859_1).contains("GPS-10.77N")); // the user's own file is not touched
    }

    @Test
    public void aJpegThatCannotBeDecodedStillLosesItsExif() throws IOException {
        // an Exif segment followed by a start-of-scan with nothing decodable: ImageIO refuses it, the segment stripper still works
        byte[] broken = new byte[]{(byte) 0xFF, (byte) 0xD8};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(broken);
        out.writeBytes(exifSegment(6, true));
        out.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xDA, 0, 2, 1, 2, 3, (byte) 0xFF, (byte) 0xD9});
        File picked = new File(home.toFile(), "odd.jpg");
        Files.write(picked.toPath(), out.toByteArray());
        String stored = ImageStorage.storeNewFile(picked);
        assertFalse(contains(Files.readAllBytes(ImageStorage.resolve(stored).toPath()), "GPS-10.77N"));
    }
}
