package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** TODO 3.43: a picture is limited by its pixels, read from the header, before anything is decoded. */
public class ImageSizeTest {

    private static byte[] encode(String format, int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    private static ImageSize.Size sizeOf(byte[] bytes) throws Exception {
        return ImageSize.read(new java.io.ByteArrayInputStream(bytes));
    }

    @Test
    public void readsPngJpegGifAndBmp() throws Exception {
        assertEquals(new ImageSize.Size(300, 200), sizeOf(encode("png", 300, 200)));
        assertEquals(new ImageSize.Size(300, 200), sizeOf(encode("jpg", 300, 200)));
        assertEquals(new ImageSize.Size(300, 200), sizeOf(encode("gif", 300, 200)));
        assertEquals(new ImageSize.Size(300, 200), sizeOf(encode("bmp", 300, 200)));
        assertEquals(new ImageSize.Size(1, 1), sizeOf(encode("png", 1, 1)));
    }

    @Test
    public void aHeaderThatClaimsHugeSizesIsReadWithoutDecoding() throws Exception {
        byte[] png = encode("png", 10, 10);
        // IHDR width and height sit at bytes 16-23: pretend 20,000 x 20,000 (a few hundred KB on disk, 1.6 GB decoded)
        png[18] = 0x4E; png[19] = 0x20; png[22] = 0x4E; png[23] = 0x20;
        ImageSize.Size size = sizeOf(png);
        assertEquals(new ImageSize.Size(20_000, 20_000), size);
        assertEquals(400_000_000L, size.pixels());
    }

    @Test
    public void aJpegWithExtraSegmentsBeforeTheFrameIsStillRead() throws Exception {
        byte[] jpg = encode("jpg", 640, 480);
        // Put a comment segment (FF FE, length 6, 4 bytes) right after the start marker.
        byte[] with = new byte[jpg.length + 8];
        with[0] = jpg[0]; with[1] = jpg[1];
        with[2] = (byte) 0xFF; with[3] = (byte) 0xFE; with[4] = 0; with[5] = 6; with[6] = 'a'; with[7] = 'b'; with[8] = 'c'; with[9] = 'd';
        System.arraycopy(jpg, 2, with, 10, jpg.length - 2);
        assertEquals(new ImageSize.Size(640, 480), sizeOf(with));
    }

    @Test
    public void thingsThatAreNotPicturesGiveNull() throws Exception {
        assertNull(sizeOf("just some text, not a picture at all".getBytes()));
        assertNull(sizeOf(new byte[0]));
        assertNull(sizeOf(new byte[]{(byte) 0x89, 'P', 'N', 'G'})); // cut off
        byte[] png = encode("png", 10, 10);
        png[12] = 'X'; // not an IHDR chunk
        assertNull(sizeOf(png));
        byte[] zero = encode("png", 10, 10);
        zero[19] = 0; // width 0
        assertNull(sizeOf(zero));
    }

    @Test
    public void sizeProblemRefusesTooManyPixelsButNotNormalPhotos() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-img");
        File normal = dir.resolve("phone.jpg").toFile();
        Files.write(normal.toPath(), encode("jpg", 4000, 3000)); // a 12-megapixel phone photo
        assertNull(ImageStorage.sizeProblem(normal));

        byte[] png = encode("png", 10, 10);
        png[18] = 0x4E; png[19] = 0x20; png[22] = 0x4E; png[23] = 0x20; // 20,000 x 20,000
        File huge = dir.resolve("huge.png").toFile();
        Files.write(huge.toPath(), png);
        String problem = ImageStorage.sizeProblem(huge);
        assertNotNull(problem);
        assertTrue(problem, problem.contains("20"));

        byte[] strip = encode("png", 10, 10);
        strip[18] = 0x4E; strip[19] = 0x20; strip[23] = 1; strip[22] = 0; // 20,000 x 1: few pixels but a silly side
        File wide = dir.resolve("strip.png").toFile();
        Files.write(wide.toPath(), strip);
        assertNotNull(ImageStorage.sizeProblem(wide));

        File text = dir.resolve("fake.png").toFile();
        Files.writeString(text.toPath(), "not a picture");
        assertNotNull(ImageStorage.sizeProblem(text));
        assertNotNull(ImageStorage.sizeProblem(dir.resolve("missing.png").toFile()));
    }
}
