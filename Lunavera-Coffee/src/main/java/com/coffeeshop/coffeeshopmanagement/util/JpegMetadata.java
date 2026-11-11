package com.coffeeshop.coffeeshopmanagement.util;

import java.io.ByteArrayOutputStream;

/**
 * What a phone puts around the picture in a JPEG file, read and removed at the byte level (TODO 3.73). No JavaFX and no AWT, so it
 * runs on any thread and is tested anywhere.
 *
 * <p>A JPEG is a list of segments, each {@code FF xx length data}. The Exif block is in an APP1 segment ({@code FF E1}, "Exif"); it
 * holds the orientation of the camera, the time, the phone model and usually the GPS position. XMP is another APP1, IPTC is APP13
 * ({@code FF ED}) and a comment is {@code FF FE}. The colour profile (APP2) and the Adobe colour marker (APP14) are not personal and
 * are needed to show the colours right, so they stay.
 */
public final class JpegMetadata {

    private JpegMetadata() {
    }

    /** The orientation tag of the Exif block, 1 to 8 as in the Exif specification; 1 (as it is) when there is none or it cannot be read. */
    public static int orientation(byte[] jpeg) {
        try {
            if (!isJpeg(jpeg)) return 1;
            int pos = 2;
            while (pos + 4 <= jpeg.length) {
                if ((jpeg[pos] & 0xFF) != 0xFF) return 1;
                int marker = jpeg[pos + 1] & 0xFF;
                if (marker == 0xFF) { // fill byte
                    pos++;
                    continue;
                }
                if (marker == 0xDA || marker == 0xD9) return 1; // the picture data begins: no Exif found before it
                int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
                if (length < 2) return 1;
                if (marker == 0xE1 && isExif(jpeg, pos + 4, length - 2)) {
                    return exifOrientation(jpeg, pos + 4 + 6, length - 2 - 6);
                }
                pos += 2 + length;
            }
        } catch (RuntimeException e) {
            return 1; // a cut-off or odd file: show it as it is
        }
        return 1;
    }

    /** The same file without Exif/XMP (APP1), IPTC (APP13) and comment segments; the bytes unchanged when this is not a JPEG. */
    public static byte[] withoutMetadata(byte[] jpeg) {
        if (!isJpeg(jpeg)) return jpeg;
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length);
        out.write(0xFF);
        out.write(0xD8);
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            if ((jpeg[pos] & 0xFF) != 0xFF) break;
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) break; // from here on it is picture data: copied as it is below
            int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            if (length < 2 || pos + 2 + length > jpeg.length) break;
            boolean drop = marker == 0xE1 || marker == 0xED || marker == 0xFE;
            if (!drop) out.write(jpeg, pos, 2 + length);
            pos += 2 + length;
        }
        out.write(jpeg, pos, jpeg.length - pos);
        return out.toByteArray();
    }

    private static boolean isJpeg(byte[] b) {
        return b != null && b.length > 4 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    private static boolean isExif(byte[] b, int start, int length) {
        return length >= 14 && start + 6 <= b.length && b[start] == 'E' && b[start + 1] == 'x' && b[start + 2] == 'i'
                && b[start + 3] == 'f' && b[start + 4] == 0 && b[start + 5] == 0;
    }

    /** {@code tiff} starts at the byte order mark ("II" or "MM") of the Exif block. */
    private static int exifOrientation(byte[] b, int tiff, int length) {
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!little && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) return 1;
        long ifd = u32(b, tiff + 4, little);
        if (ifd < 8 || ifd + 2 > length) return 1;
        int base = tiff + (int) ifd;
        int entries = u16(b, base, little);
        for (int i = 0; i < entries; i++) {
            int entry = base + 2 + 12 * i;
            if (entry + 12 > tiff + length) return 1;
            if (u16(b, entry, little) == 0x0112 && u16(b, entry + 2, little) == 3) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean little) {
        return little ? (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) : ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF);
    }

    private static long u32(byte[] b, int at, boolean little) {
        long a = u16(b, at, little);
        long c = u16(b, at + 2, little);
        return little ? a | (c << 16) : (a << 16) | c;
    }
}
