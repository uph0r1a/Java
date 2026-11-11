package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 4.53: {@link SimplePdf}, the PDF of the shift-close sheet: one A4 page with one picture. Pure Java, so the file is read back
 * here the way a reader would: the offsets of the cross-reference table, the picture's size, its pixels after the decompression, and
 * where it sits on the page. (Opened with qpdf, pdfinfo and pdftoppm when it was written; those are not run by the build.)
 */
public class SimplePdfTest {

    private static final double DELTA = 0.01;

    private static byte[] pixels(int width, int height) {
        byte[] rgb = new byte[width * height * 3];
        for (int i = 0; i < rgb.length; i++) rgb[i] = (byte) (i * 31 % 251);
        return rgb;
    }

    private static String text(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    // ---- the picture and the page

    @Test
    public void aPictureThatFitsIsCentredAtTheTopAndKeepsItsSize() {
        SimplePdf.Placement at = SimplePdf.place(300, 432, SimplePdf.A4_WIDTH, SimplePdf.A4_HEIGHT);
        assertEquals(300, at.width(), DELTA);
        assertEquals(432, at.height(), DELTA);
        assertEquals("centred across the page", (SimplePdf.A4_WIDTH - 300) / 2, at.x(), DELTA);
        assertEquals("its top edge is one margin below the top of the page", SimplePdf.A4_HEIGHT - SimplePdf.MARGIN, at.y() + at.height(), DELTA);
    }

    @Test
    public void aPictureTooBigIsMadeSmallerInProportionAndNeverBigger() {
        SimplePdf.Placement tall = SimplePdf.place(300, 1600, SimplePdf.A4_WIDTH, SimplePdf.A4_HEIGHT);
        assertEquals("it fits between the margins", SimplePdf.A4_HEIGHT - 2 * SimplePdf.MARGIN, tall.height(), DELTA);
        assertEquals("with the same proportions", 300.0 / 1600.0, tall.width() / tall.height(), 0.0001);
        SimplePdf.Placement small = SimplePdf.place(50, 80, SimplePdf.A4_WIDTH, SimplePdf.A4_HEIGHT);
        assertEquals("a small one is not blown up", 50, small.width(), DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aPictureWithoutASizeIsRefused() {
        SimplePdf.place(0, 100, SimplePdf.A4_WIDTH, SimplePdf.A4_HEIGHT);
    }

    // ---- the pixels

    @Test
    public void javaFxPixelsBlueGreenRedAlphaBecomeRedGreenBlue() {
        byte[] bgra = {10, 20, 30, (byte) 255, 1, 2, 3, (byte) 255};  // two solid pixels: blue 10 green 20 red 30, and 1 2 3
        assertTrue(Arrays.equals(new byte[]{30, 20, 10, 3, 2, 1}, SimplePdf.rgbFromBgra(bgra, 2, 1)));
    }

    @Test
    public void aTransparentPixelIsWhiteAndAHalfOneIsLightened() {
        byte[] bgra = {0, 0, 0, 0, 0, 0, 0, (byte) 128};
        byte[] rgb = SimplePdf.rgbFromBgra(bgra, 2, 1);
        assertEquals("nothing there: white", 255, rgb[0] & 0xFF);
        int half = rgb[3] & 0xFF;
        assertTrue("half black on white is grey: " + half, half > 120 && half < 135);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tooFewPixelsAreRefused() {
        SimplePdf.rgbFromBgra(new byte[7], 2, 1);
    }

    // ---- the file

    @Test
    public void theFileIsAPdfWithTheEndMarkerAndOnePage() {
        String pdf = text(SimplePdf.imagePage(20, 30, pixels(20, 30), 100, 150));
        assertTrue(pdf.startsWith("%PDF-1.4\n"));
        assertTrue(pdf.trim().endsWith("%%EOF"));
        assertTrue(pdf.contains("/Type /Catalog"));
        assertTrue(pdf.contains("/Count 1"));
        assertTrue("A4", pdf.contains("/MediaBox [0 0 595.28 841.89]"));
    }

    @Test
    public void theCrossReferenceTablePointsAtEveryObjectAndTheFinalOffsetAtTheTable() {
        byte[] bytes = SimplePdf.imagePage(20, 30, pixels(20, 30), 100, 150);
        String pdf = text(bytes);
        Matcher start = Pattern.compile("startxref\n(\\d+)\n%%EOF").matcher(pdf);
        assertTrue(start.find());
        int xref = Integer.parseInt(start.group(1));
        assertTrue("startxref points at the table", pdf.startsWith("xref\n0 6\n", xref));
        String[] entries = pdf.substring(xref + "xref\n0 6\n".length()).split("\n", 7);
        assertEquals("0000000000 65535 f ", entries[0]);
        for (int n = 1; n <= 5; n++) {
            assertEquals("an entry is 18 characters and a space", 19, entries[n].length());
            int offset = Integer.parseInt(entries[n].substring(0, 10));
            assertTrue("entry " + n + " points at \"" + n + " 0 obj\"", pdf.startsWith(n + " 0 obj\n", offset));
        }
    }

    @Test
    public void thePictureHasItsPixelSizeAndTheRightColourSpace() {
        String pdf = text(SimplePdf.imagePage(20, 30, pixels(20, 30), 100, 150));
        assertTrue(pdf.contains("/Subtype /Image /Width 20 /Height 30"));
        assertTrue(pdf.contains("/ColorSpace /DeviceRGB /BitsPerComponent 8"));
        assertTrue(pdf.contains("/Filter /FlateDecode"));
    }

    @Test
    public void thePicturesStreamHasTheDeclaredLengthAndUnpacksToTheSamePixels() throws Exception {
        byte[] rgb = pixels(20, 30);
        byte[] bytes = SimplePdf.imagePage(20, 30, rgb, 100, 150);
        String pdf = text(bytes);
        Matcher image = Pattern.compile("5 0 obj\n<<[^>]*?/Length (\\d+) >>\nstream\n").matcher(pdf);
        assertTrue(image.find());
        int length = Integer.parseInt(image.group(1));
        int from = image.end();
        assertEquals("the stream ends where it says", "\nendstream", pdf.substring(from + length, from + length + 10));

        Inflater inflater = new Inflater();
        inflater.setInput(bytes, from, length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!inflater.finished()) {
            int n = inflater.inflate(buffer);
            if (n == 0 && inflater.needsInput()) break;
            out.write(buffer, 0, n);
        }
        assertTrue("the pixels come back unchanged", Arrays.equals(rgb, out.toByteArray()));
    }

    @Test
    public void theContentPutsThePictureWhereTheyPlaceItBelowTheTopMargin() {
        String pdf = text(SimplePdf.imagePage(20, 30, pixels(20, 30), 300, 432));
        SimplePdf.Placement at = SimplePdf.place(300, 432, SimplePdf.A4_WIDTH, SimplePdf.A4_HEIGHT);
        String expected = String.format(java.util.Locale.ROOT, "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q", at.width(), at.height(), at.x(), at.y());
        assertTrue(expected + " in the content", pdf.contains(expected));
    }

    @Test(expected = IllegalArgumentException.class)
    public void pixelsThatDoNotFitTheSizeAreRefused() {
        SimplePdf.imagePage(20, 30, new byte[10], 100, 150);
    }

    @Test
    public void aSheetSizedPictureStaysSmall() {
        // a mostly white picture of the size of a printed sheet (300 dots an inch): the file must stay a few hundred KB at most
        int width = 1250;
        int height = 1800;
        byte[] rgb = new byte[width * height * 3];
        Arrays.fill(rgb, (byte) 255);
        for (int y = 100; y < 1700; y += 40) for (int x = 100; x < 1100; x++) rgb[(y * width + x) * 3] = 0;
        byte[] pdf = SimplePdf.imagePage(width, height, rgb, 300, 432);
        assertTrue("size " + pdf.length, pdf.length < 300_000);
    }
}
