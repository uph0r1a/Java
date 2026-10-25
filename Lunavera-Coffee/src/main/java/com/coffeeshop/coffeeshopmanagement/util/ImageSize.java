package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The width and height of a picture, read from the first bytes of the file without decoding it (TODO 3.43). A flat-colour PNG of
 * 20,000 x 20,000 pixels is a few hundred KB on disk and needs 1.6 GB once decoded, so a limit on the file size says nothing
 * about the memory a picture needs; this does. Knows PNG, JPEG (also JFIF, progressive), GIF and BMP, the picture types the
 * product form accepts. No JavaFX and no AWT, so it can run on any thread and be tested anywhere.
 */
public final class ImageSize {

    private ImageSize() {
    }

    /** Width and height in pixels. */
    public record Size(int width, int height) {
        public long pixels() {
            return (long) width * height;
        }
    }

    /** Reads the size from the file's start; null when the file is not one of the known picture types or its header is cut off. */
    public static Size read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static Size read(InputStream in) throws IOException {
        byte[] head = in.readNBytes(30);
        if (head.length >= 24 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            // 8 bytes signature, 4 length, "IHDR", then width and height as 4 bytes each, big-endian.
            if (head[12] != 'I' || head[13] != 'H' || head[14] != 'D' || head[15] != 'R') return null;
            return sized(bigEndian(head, 16), bigEndian(head, 20));
        }
        if (head.length >= 10 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F') {
            return sized(littleEndian16(head, 6), littleEndian16(head, 8));
        }
        if (head.length >= 26 && head[0] == 'B' && head[1] == 'M') {
            int headerSize = littleEndian32(head, 14);
            if (headerSize == 12) { // the old OS/2 header: 16-bit sizes
                return sized(littleEndian16(head, 18), littleEndian16(head, 20));
            }
            long width = littleEndian32(head, 18);
            long height = Math.abs((long) littleEndian32(head, 22)); // a negative height means rows stored top-down
            return sized(width, height);
        }
        if (head.length >= 4 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8) {
            return readJpeg(head, in);
        }
        return null;
    }

    /** Walks the JPEG segments until a start-of-frame marker, which holds the height and the width. */
    private static Size readJpeg(byte[] head, InputStream in) throws IOException {
        // The first two bytes (FF D8) are already read; put back the rest of what was read ahead.
        java.io.PushbackInputStream pushed = new java.io.PushbackInputStream(in, head.length);
        pushed.unread(head, 2, head.length - 2);
        for (int segments = 0; segments < 200; segments++) {
            int b = pushed.read();
            while (b != -1 && b != 0xFF) b = pushed.read(); // skip to the next marker
            int marker = pushed.read();
            while (marker == 0xFF) marker = pushed.read(); // fill bytes
            if (b == -1 || marker == -1) return null;
            if (marker == 0xD9 || marker == 0xDA) return null; // end of picture / start of data without a frame header
            if (marker == 0x00 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) continue; // markers without a length
            int hi = pushed.read();
            int lo = pushed.read();
            if (hi == -1 || lo == -1) return null;
            int length = (hi << 8) | lo;
            boolean startOfFrame = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (startOfFrame) {
                byte[] frame = pushed.readNBytes(5); // precision, height (2), width (2)
                if (frame.length < 5) return null;
                int height = ((frame[1] & 0xFF) << 8) | (frame[2] & 0xFF);
                int width = ((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF);
                return sized(width, height);
            }
            if (length < 2) return null;
            pushed.skipNBytes(length - 2);
        }
        return null;
    }

    private static Size sized(long width, long height) {
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) return null;
        return new Size((int) width, (int) height);
    }

    private static int bigEndian(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static int littleEndian16(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8);
    }

    private static int littleEndian32(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) | ((b[at + 2] & 0xFF) << 16) | ((b[at + 3] & 0xFF) << 24);
    }
}
