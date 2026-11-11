package com.coffeeshop.coffeeshopmanagement.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;

/**
 * A one-page PDF that holds one picture (TODO 4.53): the shift-close sheet, drawn as it is on paper and put in the middle of an A4
 * page. The sheet is saved as a picture so that the Vietnamese letters are exactly as printed, with no font to embed and no library
 * to add to the program; the figures for a spreadsheet are in the CSV. Pure Java, no JavaFX, so it is tested on its own.
 *
 * <p>The file is a plain PDF 1.4: a catalog, one page, the page's content, and the picture as a Flate-compressed RGB image.
 */
public final class SimplePdf {

    /** A4 in points (1/72 inch). */
    public static final double A4_WIDTH = 595.28;
    public static final double A4_HEIGHT = 841.89;

    /** The empty edge kept around the picture, in points. */
    static final double MARGIN = 36;

    private SimplePdf() {
    }

    /** Where the picture goes on the page, in points from the bottom left corner. */
    public record Placement(double x, double y, double width, double height) {
    }

    /**
     * The picture {@code width} x {@code height} points big, centred across the page and set at the top below the margin. A picture too
     * big for the page inside its margins is made smaller in proportion, never bigger.
     */
    public static Placement place(double width, double height, double pageWidth, double pageHeight) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("The picture has no size");
        double scale = Math.min(1.0, Math.min((pageWidth - 2 * MARGIN) / width, (pageHeight - 2 * MARGIN) / height));
        double w = width * scale;
        double h = height * scale;
        return new Placement((pageWidth - w) / 2, pageHeight - MARGIN - h, w, h);
    }

    /**
     * The picture as RGB bytes (three a pixel, row after row), from pixels given as blue, green, red, alpha, the order JavaFX hands
     * them out in. A pixel that is not fully solid is laid on white, as it would be on paper.
     */
    public static byte[] rgbFromBgra(byte[] bgra, int width, int height) {
        if (bgra.length < width * height * 4) throw new IllegalArgumentException("Too few pixels");
        byte[] rgb = new byte[width * height * 3];
        for (int i = 0, from = 0, to = 0; i < width * height; i++, from += 4, to += 3) {
            int alpha = bgra[from + 3] & 0xFF;
            for (int channel = 0; channel < 3; channel++) {
                int value = bgra[from + 2 - channel] & 0xFF; // b,g,r -> r,g,b
                rgb[to + channel] = (byte) (alpha == 255 ? value : (value * alpha + 255 * (255 - alpha)) / 255);
            }
        }
        return rgb;
    }

    /**
     * The PDF of an A4 page with the picture on it.
     *
     * @param pixelWidth the picture's width in pixels
     * @param pixelHeight the picture's height in pixels
     * @param rgb the pixels, three bytes each (see {@link #rgbFromBgra})
     * @param widthPoints how wide the picture should be on the page, in points
     * @param heightPoints how high
     */
    public static byte[] imagePage(int pixelWidth, int pixelHeight, byte[] rgb, double widthPoints, double heightPoints) {
        if (pixelWidth <= 0 || pixelHeight <= 0 || rgb.length != pixelWidth * pixelHeight * 3) {
            throw new IllegalArgumentException("The pixels do not fit the size of the picture");
        }
        Placement at = place(widthPoints, heightPoints, A4_WIDTH, A4_HEIGHT);
        byte[] image = deflate(rgb);
        String content = String.format(Locale.ROOT, "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q", at.width(), at.height(), at.x(), at.y());
        byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(image.length + 1024);
            List<Integer> offsets = new ArrayList<>();
            out.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));
            offsets.add(out.size());
            out.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
            offsets.add(out.size());
            out.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
            offsets.add(out.size());
            out.write(String.format(Locale.ROOT, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2f %.2f] "
                    + "/Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>\nendobj\n", A4_WIDTH, A4_HEIGHT)
                    .getBytes(StandardCharsets.US_ASCII));
            offsets.add(out.size());
            out.write(("4 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII));
            out.write(contentBytes);
            out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));
            offsets.add(out.size());
            out.write(("5 0 obj\n<< /Type /XObject /Subtype /Image /Width " + pixelWidth + " /Height " + pixelHeight
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Interpolate true /Filter /FlateDecode /Length " + image.length
                    + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII));
            out.write(image);
            out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));
            int xref = out.size();
            StringBuilder table = new StringBuilder("xref\n0 6\n0000000000 65535 f \n");
            for (int offset : offsets) table.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
            table.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
            out.write(table.toString().getBytes(StandardCharsets.US_ASCII));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the PDF", e); // a byte array does not fail to write
        }
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, data.length / 8));
            byte[] buffer = new byte[16 * 1024];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
