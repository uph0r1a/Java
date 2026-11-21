package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (second run, {@link JpegMetadata}): the byte-level edges. The older tests used a few real
 * phone pictures and a hand-made one; a boundary moved by one byte, a segment that is empty, a fill byte, or an orientation outside
 * 1 to 8 went unnoticed.
 */
public class JpegMetadataEdgesTest {

    private static final byte[] SOI = {(byte) 0xFF, (byte) 0xD8};
    private static final byte[] EOI = {(byte) 0xFF, (byte) 0xD9};

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part, 0, part.length);
        return out.toByteArray();
    }

    private static byte[] segment(int marker, byte[] payload) {
        int length = payload.length + 2;
        return cat(new byte[]{(byte) 0xFF, (byte) marker, (byte) (length >> 8), (byte) length}, payload);
    }

    private static byte[] u16(boolean little, int v) {
        return little ? new byte[]{(byte) v, (byte) (v >> 8)} : new byte[]{(byte) (v >> 8), (byte) v};
    }

    private static byte[] u32(boolean little, long v) {
        return little ? new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}
                : new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    /** An entry of the Exif directory: tag, type, count 1, a two-byte value and two bytes of padding. */
    private static byte[] entry(boolean little, int tag, int type, int value) {
        return cat(u16(little, tag), u16(little, type), u32(little, 1), u16(little, value), new byte[2]);
    }

    /** The payload of an APP1 Exif segment holding the given directory entries; {@code nextIfd} adds the four bytes that end a directory. */
    private static byte[] exif(boolean little, long ifdOffset, boolean nextIfd, byte[]... entries) {
        byte[] tiff = cat(new byte[]{(byte) (little ? 'I' : 'M'), (byte) (little ? 'I' : 'M')}, u16(little, 42), u32(little, ifdOffset));
        byte[] directory = cat(u16(little, entries.length), cat(entries), nextIfd ? new byte[4] : new byte[0]);
        return cat("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), tiff, directory);
    }

    private static byte[] jpegWith(byte[]... segments) {
        return cat(SOI, cat(segments), segment(0xDA, new byte[]{1, 2, 3}), new byte[]{9, 9, 9}, EOI);
    }

    private static int orientationOf(boolean little, int value) {
        return JpegMetadata.orientation(jpegWith(segment(0xE1, exif(little, 8, true, entry(little, 0x0112, 3, value)))));
    }

    // ---- orientation

    @Test
    public void everyOrientationFromOneToEightIsReadInBothByteOrders() {
        for (int value = 1; value <= 8; value++) {
            assertEquals("little-endian " + value, value, orientationOf(true, value));
            assertEquals("big-endian " + value, value, orientationOf(false, value));
        }
    }

    @Test
    public void anOrientationOutsideOneToEightIsTakenAsOne() {
        assertEquals(1, orientationOf(true, 0));
        assertEquals(1, orientationOf(true, 9));
        assertEquals(1, orientationOf(false, 0));
        assertEquals(1, orientationOf(false, 9));
        assertEquals(1, orientationOf(true, 0xFFFF));
    }

    @Test
    public void theOrientationEntryMayBeAnyEntryOfTheDirectoryButMustBeOfTypeShort() {
        byte[] other = entry(true, 0x010F, 2, 5);
        assertEquals("second entry", 6, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 8, true, other, entry(true, 0x0112, 3, 6))))));
        assertEquals("third entry", 8, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 8, true, other, other, entry(true, 0x0112, 3, 8))))));
        assertEquals("type 4 (long) is not an orientation", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 8, true, entry(true, 0x0112, 4, 6))))));
        assertEquals("no orientation entry", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 8, true, other)))));
        assertEquals("a directory without entries", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 8, true)))));
    }

    @Test
    public void theLastEntryMayEndExactlyWhereTheBlockEnds() {
        assertEquals("no next-directory offset after the entry", 6, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 8, false, entry(true, 0x0112, 3, 6))))));
        assertEquals(6, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(false, 8, false, entry(false, 0x0112, 3, 6))))));
        byte[] cut = exif(true, 8, false, entry(true, 0x0112, 3, 6));
        assertEquals("one byte short of the entry", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, Arrays.copyOf(cut, cut.length - 1)))));
    }

    @Test
    public void theDirectoryMustStartAtByteEightOrLaterAndInsideTheBlock() {
        assertEquals("offset 7 is inside the header", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 7, true, entry(true, 0x0112, 3, 6))))));
        assertEquals("offset 8", 6, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 8, true, entry(true, 0x0112, 3, 6))))));
        assertEquals("offset far outside the block", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, exif(true, 5000, true, entry(true, 0x0112, 3, 6))))));
        assertEquals("a byte order that is neither II nor MM", 1,
                JpegMetadata.orientation(jpegWith(segment(0xE1, cat("Exif\0\0".getBytes(), "XX".getBytes(), new byte[]{0, 42, 0, 0, 0, 8})))));
        byte[] mixed = exif(true, 8, true, entry(true, 0x0112, 3, 6));
        mixed[6 + 1] = 'M';
        assertEquals("I followed by M is no byte order", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, mixed))));
    }

    @Test
    public void theExifBlockMayComeAfterOtherSegmentsEvenEmptyOnesButNotAfterThePictureData() {
        byte[] exifSegment = segment(0xE1, exif(true, 8, true, entry(true, 0x0112, 3, 3)));
        assertEquals("after a JFIF segment", 3, JpegMetadata.orientation(jpegWith(segment(0xE0, new byte[14]), exifSegment)));
        assertEquals("after an empty segment (length 2)", 3, JpegMetadata.orientation(jpegWith(segment(0xE0, new byte[0]), exifSegment)));
        assertEquals("after fill bytes", 3, JpegMetadata.orientation(cat(SOI, new byte[]{(byte) 0xFF, (byte) 0xFF}, exifSegment, segment(0xDA, new byte[]{1}), EOI)));
        assertEquals("after the picture data begins", 1, JpegMetadata.orientation(cat(SOI, segment(0xDA, new byte[]{1, 2, 3}), exifSegment, EOI)));
        assertEquals("after the end marker", 1, JpegMetadata.orientation(cat(SOI, EOI, exifSegment)));
        assertEquals("an APP1 that is not Exif (XMP) is passed over", 3,
                JpegMetadata.orientation(jpegWith(segment(0xE1, "http://ns.adobe.com/xap/1.0/\0".getBytes()), exifSegment)));
    }

    @Test
    public void aBrokenOrCutFileIsShownAsItIsNeverWithAnException() {
        assertEquals(1, JpegMetadata.orientation(null));
        assertEquals(1, JpegMetadata.orientation(new byte[0]));
        assertEquals("four bytes are no picture", 1, JpegMetadata.orientation(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1}));
        assertEquals("not a JPEG", 1, JpegMetadata.orientation("GIF89a....".getBytes()));
        assertEquals("a segment that does not start with FF", 1, JpegMetadata.orientation(cat(SOI, new byte[]{1, 2, 3, 4, 5, 6})));
        assertEquals("a segment shorter than its own length field", 1, JpegMetadata.orientation(cat(SOI, new byte[]{(byte) 0xFF, (byte) 0xE0, 0, 1, 0, 0})));
        byte[] whole = jpegWith(segment(0xE1, exif(true, 8, true, entry(true, 0x0112, 3, 6))));
        for (int cut = 0; cut < whole.length; cut++) {
            int result = JpegMetadata.orientation(Arrays.copyOf(whole, cut));
            assertTrue("cut at " + cut + " gave " + result, result >= 1 && result <= 8);
        }
    }

    @Test
    public void aFileOfFiveBytesIsAJpegAndOfFourBytesIsNot() {
        byte[] five = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0};
        byte[] four = Arrays.copyOf(five, 4);
        assertSame("four bytes: handed back as they are", four, JpegMetadata.withoutMetadata(four));
        byte[] result = JpegMetadata.withoutMetadata(five);
        assertArrayEquals("five bytes: a JPEG without anything to remove", five, result);
        assertTrue("and a new array", result != five);
    }

    // ---- withoutMetadata

    @Test
    public void exifXmpIptcAndCommentAreDroppedAndEverythingElseStays() {
        byte[] jfif = segment(0xE0, new byte[14]);
        byte[] icc = segment(0xE2, "ICC_PROFILE\0".getBytes());
        byte[] adobe = segment(0xEE, "Adobe".getBytes());
        byte[] table = segment(0xDB, new byte[65]);
        byte[] before = cat(SOI, jfif, segment(0xE1, exif(true, 8, true, entry(true, 0x0112, 3, 6))), segment(0xED, new byte[20]), icc,
                segment(0xFE, "comment".getBytes()), adobe, segment(0xE1, "xmp".getBytes()), table);
        byte[] tail = cat(segment(0xDA, new byte[]{1, 2, 3}), new byte[]{9, 9, 9}, EOI);
        assertArrayEquals(cat(SOI, jfif, icc, adobe, table, tail), JpegMetadata.withoutMetadata(cat(before, tail)));
    }

    @Test
    public void whatComesAfterThePictureDataIsNeverTouchedEvenWhenItLooksLikeASegment() {
        byte[] fake = cat(new byte[]{(byte) 0xFF, (byte) 0xE1, 0, 4, 7, 7});
        byte[] jpeg = cat(SOI, segment(0xE0, new byte[14]), segment(0xDA, new byte[]{1}), fake, EOI);
        assertArrayEquals(jpeg, JpegMetadata.withoutMetadata(jpeg));
    }

    @Test
    public void theEndMarkerStopsTheScanLikeThePictureData() {
        byte[] jpeg = cat(SOI, segment(0xE0, new byte[2]), EOI, segment(0xE1, new byte[10]));
        assertArrayEquals("the APP1 after the end marker is copied as it is", jpeg, JpegMetadata.withoutMetadata(jpeg));
    }

    @Test
    public void anEmptyAppOneSegmentAtTheVeryEndIsStillDropped() {
        byte[] jpeg = cat(SOI, new byte[]{(byte) 0xFF, (byte) 0xE1, 0, 2});
        assertArrayEquals("four bytes after the start marker are enough to be a segment", SOI, JpegMetadata.withoutMetadata(jpeg));
    }

    @Test
    public void aSegmentThatIsTooShortOrRunsPastTheEndStopsTheCleaningAndKeepsTheRest() {
        byte[] tooShort = cat(SOI, new byte[]{(byte) 0xFF, (byte) 0xE1, 0, 1, 5, 5, 5});
        assertArrayEquals("a length of 1 is not a length", tooShort, JpegMetadata.withoutMetadata(tooShort));
        byte[] pastEnd = cat(SOI, new byte[]{(byte) 0xFF, (byte) 0xE1, 0, 20, 5, 5, 5});
        assertArrayEquals("a length that reaches past the end", pastEnd, JpegMetadata.withoutMetadata(pastEnd));
        byte[] exactEnd = cat(SOI, new byte[]{(byte) 0xFF, (byte) 0xE1, 0, 5, 1, 2, 3});
        assertArrayEquals("a segment that ends exactly at the end is dropped", SOI, JpegMetadata.withoutMetadata(exactEnd));
        byte[] notASegment = cat(SOI, new byte[]{1, 2, 3, 4, 5, 6});
        assertArrayEquals(notASegment, JpegMetadata.withoutMetadata(notASegment));
    }

    @Test
    public void aFillByteBeforeASegmentIsLeftOutAndTheSegmentIsCleanedAsUsual() {
        byte[] app0 = segment(0xE0, new byte[2]);
        byte[] jpeg = cat(SOI, new byte[]{(byte) 0xFF}, app0, segment(0xE1, new byte[10]), EOI);
        assertArrayEquals(cat(SOI, app0, EOI), JpegMetadata.withoutMetadata(jpeg));
    }

    @Test
    public void aNullOrNonJpegFileIsHandedBackUnchanged() {
        assertNull(JpegMetadata.withoutMetadata(null));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};
        assertSame(png, JpegMetadata.withoutMetadata(png));
    }

    @Test
    public void theEndMarkerStopsTheSearchEvenWhenWhatFollowsLooksLikeASegmentLength() {
        byte[] exifSegment = segment(0xE1, exif(true, 8, true, entry(true, 0x0112, 3, 6)));
        // FF D9 has no length; the two bytes after it must not be taken for one
        assertEquals(1, JpegMetadata.orientation(cat(SOI, EOI, new byte[]{0, 2}, exifSegment, segment(0xDA, new byte[]{1}))));
    }

    @Test
    public void theEndMarkerStopsTheCleaningEvenWhenWhatFollowsLooksLikeASegmentLength() {
        byte[] jpeg = cat(SOI, EOI, new byte[]{0, 2}, segment(0xE1, new byte[]{1, 2}));
        assertArrayEquals(jpeg, JpegMetadata.withoutMetadata(jpeg));
    }

    @Test
    public void aSegmentLongerThanTwoHundredFiftySixBytesIsSkippedWholeBothWays() {
        byte[] longSegment = segment(0xE2, new byte[300]);
        byte[] exifSegment = segment(0xE1, exif(true, 8, true, entry(true, 0x0112, 3, 5)));
        assertEquals("orientation after a long segment", 5, JpegMetadata.orientation(jpegWith(longSegment, exifSegment)));
        byte[] tail = cat(segment(0xDA, new byte[]{1}), EOI);
        assertArrayEquals("a long segment is kept whole, the APP1 after it dropped",
                cat(SOI, longSegment, tail), JpegMetadata.withoutMetadata(cat(SOI, longSegment, exifSegment, tail)));
    }

    @Test
    public void onlyAsManyEntriesAsTheDirectoryStatesAreRead() {
        byte[] other = entry(true, 0x010F, 2, 5);
        byte[] payload = cat(exif(true, 8, false, other), entry(true, 0x0112, 3, 6));
        assertEquals("the bytes after the last entry are not an entry", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, payload))));
    }

    /** An Exif block whose header points at 65,544 (high half 1, low half 8) with a valid directory at byte 264. */
    private static byte[] blockWithAnOffsetOfSixtyFiveThousand(boolean little) {
        byte[] header = cat("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                new byte[]{(byte) (little ? 'I' : 'M'), (byte) (little ? 'I' : 'M')}, u16(little, 42), u32(little, 0x00010008L));
        byte[] gap = new byte[6 + 264 - header.length];
        byte[] directory = cat(u16(little, 1), entry(little, 0x0112, 3, 6), new byte[4]);
        return cat(header, gap, directory);
    }

    @Test
    public void aDirectoryOffsetUsesAllFourBytesInBothByteOrders() {
        assertEquals("little-endian: 65,544 is far outside the block", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, blockWithAnOffsetOfSixtyFiveThousand(true)))));
        assertEquals("big-endian: 65,544 is far outside the block", 1, JpegMetadata.orientation(jpegWith(segment(0xE1, blockWithAnOffsetOfSixtyFiveThousand(false)))));
    }

    @Test
    public void aDirectoryAtOffsetThreeHundredIsFound() {
        for (boolean little : new boolean[]{true, false}) {
            byte[] header = cat("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                    new byte[]{(byte) (little ? 'I' : 'M'), (byte) (little ? 'I' : 'M')}, u16(little, 42), u32(little, 300));
            byte[] gap = new byte[6 + 300 - header.length];
            byte[] block = cat(header, gap, u16(little, 1), entry(little, 0x0112, 3, 7), new byte[4]);
            assertEquals("little=" + little, 7, JpegMetadata.orientation(jpegWith(segment(0xE1, block))));
        }
    }
}
