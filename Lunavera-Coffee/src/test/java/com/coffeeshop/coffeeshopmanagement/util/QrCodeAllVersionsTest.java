package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * TODO 6.60: {@link QrCodeTest} pins one symbol of version 1. This one reads the encoder's output back with {@link QrDecoder}
 * (a separate reader written from the standard) for every one of the 40 versions: alignment patterns (version 2 and up), version
 * information (7 and up), several Reed-Solomon blocks interleaved (5 and up) and the 16-bit length field (10 and up) are all
 * checked, at the length where each version starts and at the length where it ends.
 */
public class QrCodeAllVersionsTest {

    /** Plain text of exactly {@code bytes} bytes (printable ASCII from a fixed seed, so a failure repeats). */
    private static String text(int bytes, long seed) {
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder(bytes);
        for (int i = 0; i < bytes; i++) sb.append((char) (' ' + random.nextInt(95)));
        return sb.toString();
    }

    private static QrDecoder.Result roundTrip(String text) {
        QrCode code = QrCode.encodeText(text);
        QrDecoder.Result result = QrDecoder.decode(code);
        assertEquals("the text read back", text, result.text());
        assertEquals("version read from the size", code.getVersion(), result.version());
        assertEquals("mask in the format information", code.getMask(), result.mask());
        return result;
    }

    @Test
    public void everyVersionIsChosenAndReadBackAtItsLargestText() {
        for (int version = 1; version <= 40; version++) {
            int capacity = QrDecoder.BYTE_CAPACITY_M[version - 1];
            QrCode code = QrCode.encodeText(text(capacity, version));
            assertEquals("version for " + capacity + " bytes", version, code.getVersion());
            assertEquals(version, roundTrip(text(capacity, version)).version());
        }
    }

    @Test
    public void oneByteMoreMovesToTheNextVersionAndStillReadsBack() {
        for (int version = 1; version < 40; version++) {
            int length = QrDecoder.BYTE_CAPACITY_M[version - 1] + 1;
            String text = text(length, 1000 + version);
            assertEquals("version for " + length + " bytes", version + 1, QrCode.encodeText(text).getVersion());
            assertEquals(version + 1, roundTrip(text).version());
        }
    }

    @Test
    public void theSmallestTextOfEachVersionReadsBack() {
        for (int version = 2; version <= 40; version++) {
            int length = QrDecoder.BYTE_CAPACITY_M[version - 2] + 1; // the shortest text that needs this version
            assertEquals(version, roundTrip(text(length, 2000 + version)).version());
        }
    }

    @Test
    public void anEmptyTextAndASingleCharacterReadBack() {
        assertEquals(1, roundTrip("").version());
        assertEquals(1, roundTrip("A").version());
    }

    @Test
    public void theLongestTextReadsBack() {
        QrDecoder.Result result = roundTrip(text(QrCode.MAX_BYTES, 77));
        assertEquals(40, result.version());
    }

    /** More than 255 bytes needs the 16-bit count field of version 10 and up. */
    @Test
    public void aTextOfMoreThanTwoFiftyFiveBytesUsesTheLongCountField() {
        String text = text(256, 5);
        assertTrue(QrCode.encodeText(text).getVersion() >= 10);
        assertEquals(text, QrDecoder.decode(QrCode.encodeText(text)).text());
        assertEquals(text(1000, 6), QrDecoder.decode(QrCode.encodeText(text(1000, 6))).text());
    }

    @Test
    public void vietnameseTextReadsBackAcrossVersionChanges() {
        String unit = "\u0110\u1eb7t h\u00e0ng c\u00e0 ph\u00ea s\u1eefa \u0111\u00e1 - b\u00e0n 5 \u2192 45.000\u0111; ";
        StringBuilder sb = new StringBuilder();
        int lastVersion = 0;
        int versions = 0;
        for (int i = 0; i < 70; i++) {
            sb.append(unit);
            String text = sb.toString();
            if (text.getBytes(StandardCharsets.UTF_8).length > QrCode.MAX_BYTES) break;
            int version = roundTrip(text).version();
            if (version != lastVersion) versions++;
            lastVersion = version;
        }
        assertTrue("the growing text crossed several versions (" + versions + ")", versions >= 8);
    }

    /** The receipt's transfer note and the kind of text a payment QR holds, at their usual (short) length. */
    @Test
    public void aTransferNoteReadsBack() {
        roundTrip(OrderNumber.transferNote(12));
        roundTrip("00020101021238570010A000000727012700069704220113VQRQ0000000000208QRIBFTTA5303704540545000" + "5802VN6304ABCD");
    }

    @Test
    public void everyMaskReadsBackAtVersionsWithDifferentFeatures() {
        // 1: nothing extra; 2: first alignment pattern; 7: version information; 10: 16-bit count; 14: four alignment centres; 40: all
        int[] versions = {1, 2, 5, 7, 10, 14, 25, 40};
        for (int version : versions) {
            String text = text(QrDecoder.BYTE_CAPACITY_M[version - 1], 300 + version);
            for (int mask = 0; mask < 8; mask++) {
                QrCode code = QrCode.encodeText(text, mask);
                QrDecoder.Result result = QrDecoder.decode(code);
                assertEquals("mask " + mask + " at version " + version, mask, result.mask());
                assertEquals(text, result.text());
                assertEquals(version, result.version());
            }
        }
    }

    /** The decoder must not be a rubber stamp: damage one module and it has to notice. */
    @Test
    public void theDecoderNoticesADamagedSymbol() {
        for (int version : new int[] {1, 6, 7, 20}) {
            QrCode code = QrCode.encodeText(text(QrDecoder.BYTE_CAPACITY_M[version - 1], 900 + version));
            int size = code.getSize();
            boolean[][] grid = new boolean[size][size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) grid[y][x] = code.getModule(x, y);
            QrDecoder.decode(grid); // intact: fine

            boolean[][] data = copy(grid);
            data[size - 1][size - 1] = !data[size - 1][size - 1]; // a data module in the corner
            assertRefused("a flipped data module (version " + version + ")", data);

            boolean[][] format = copy(grid);
            format[8][0] = !format[8][0]; // one copy of the format information
            assertRefused("a flipped format bit (version " + version + ")", format);
        }
        QrCode v7 = QrCode.encodeText(text(QrDecoder.BYTE_CAPACITY_M[6], 7));
        boolean[][] grid = new boolean[v7.getSize()][v7.getSize()];
        for (int y = 0; y < grid.length; y++) for (int x = 0; x < grid.length; x++) grid[y][x] = v7.getModule(x, y);
        grid[0][grid.length - 11] = !grid[0][grid.length - 11]; // version information
        assertRefused("a flipped version-information bit", grid);
    }

    private static boolean[][] copy(boolean[][] grid) {
        boolean[][] copy = new boolean[grid.length][];
        for (int i = 0; i < grid.length; i++) copy[i] = grid[i].clone();
        return copy;
    }

    private static void assertRefused(String what, boolean[][] grid) {
        try {
            QrDecoder.decode(grid);
        } catch (AssertionError expected) {
            return;
        }
        fail("the decoder accepted " + what);
    }
}
