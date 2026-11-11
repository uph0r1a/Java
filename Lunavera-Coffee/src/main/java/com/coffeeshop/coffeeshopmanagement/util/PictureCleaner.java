package com.coffeeshop.coffeeshopmanagement.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Makes the picture that is stored from the picture that was picked (TODO 3.73): turned the right way up by its Exif orientation,
 * no longer than {@link #MAX_SIDE} pixels on its long side, and written fresh, so nothing of the original file's metadata (the GPS
 * position, the phone model, the time) is in it. JavaFX's {@code Image} ignores the orientation tag, so a portrait phone photo
 * showed on its side before. Uses AWT's {@code ImageIO}, no JavaFX; call it off the screen thread.
 *
 * <p>JPEG stays JPEG (quality 0.9, no alpha); PNG stays PNG with its transparency; a GIF or BMP is stored as PNG (a GIF's
 * animation is lost: a product picture is one picture).
 */
public final class PictureCleaner {

    /** The longest side stored. The product cards and list show a picture far smaller than this; a 12-megapixel photo is about 20 times larger. */
    public static final int MAX_SIDE = 1024;
    private static final float JPEG_QUALITY = 0.9f;

    private PictureCleaner() {
    }

    /** The cleaned file's bytes and the extension to store them under ({@code jpg} or {@code png}). */
    public record Cleaned(byte[] bytes, String extension) {
    }

    /**
     * @param original  the picked file's bytes
     * @param extension its lowercase extension without the dot
     * @return the cleaned picture, or null when the bytes cannot be decoded here (an unusual JPEG such as CMYK, a broken file): the
     *         caller then keeps the file and at least strips the metadata of a JPEG with {@link JpegMetadata#withoutMetadata}
     */
    public static Cleaned clean(byte[] original, String extension) throws IOException {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(original));
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (image == null) return null;
        boolean jpeg = extension.equals("jpg") || extension.equals("jpeg") || extension.equals("jpe") || extension.equals("jfif");
        int orientation = jpeg ? JpegMetadata.orientation(original) : 1;
        boolean alpha = image.getColorModel().hasAlpha() && !jpeg;
        BufferedImage upright = oriented(scaledDown(image, alpha), orientation, alpha);
        return jpeg ? new Cleaned(writeJpeg(upright), "jpg") : new Cleaned(writePng(upright), "png");
    }

    /** The size a picture of {@code width} x {@code height} is stored at: unchanged when it is small enough, else the long side is {@link #MAX_SIDE}. */
    public static int[] fitted(int width, int height) {
        int longest = Math.max(width, height);
        if (longest <= MAX_SIDE) return new int[]{width, height};
        double factor = (double) MAX_SIDE / longest;
        return new int[]{Math.max(1, (int) Math.round(width * factor)), Math.max(1, (int) Math.round(height * factor))};
    }

    /**
     * The 3x2 matrix that puts a picture of {@code w} x {@code h} the right way up for Exif orientation 1 to 8: {@code {m00, m10, m01, m11, m02, m12}}
     * of {@code x' = m00 x + m01 y + m02, y' = m10 x + m11 y + m12}.
     */
    static double[] matrix(int orientation, int w, int h) {
        return switch (orientation) {
            case 2 -> new double[]{-1, 0, 0, 1, w, 0};      // mirrored left to right
            case 3 -> new double[]{-1, 0, 0, -1, w, h};     // upside down
            case 4 -> new double[]{1, 0, 0, -1, 0, h};      // mirrored top to bottom
            case 5 -> new double[]{0, 1, 1, 0, 0, 0};       // transposed
            case 6 -> new double[]{0, 1, -1, 0, h, 0};      // turned a quarter clockwise (the usual portrait photo)
            case 7 -> new double[]{0, -1, -1, 0, h, w};     // transversed
            case 8 -> new double[]{0, -1, 1, 0, 0, w};      // turned a quarter anticlockwise
            default -> new double[]{1, 0, 0, 1, 0, 0};
        };
    }

    static BufferedImage oriented(BufferedImage source, int orientation, boolean alpha) {
        if (orientation < 2 || orientation > 8) return source;
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swaps = orientation >= 5;
        BufferedImage out = new BufferedImage(swaps ? h : w, swaps ? w : h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        double[] m = matrix(orientation, w, h);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, new AffineTransform(m[0], m[1], m[2], m[3], m[4], m[5]), null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Halves the picture while it is more than twice too big, then makes the last step: sharper than one big jump. Also converts to plain RGB(A), which applies a colour profile. */
    static BufferedImage scaledDown(BufferedImage source, boolean alpha) {
        int[] target = fitted(source.getWidth(), source.getHeight());
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage current = source;
        int w = source.getWidth();
        int h = source.getHeight();
        boolean converted = false;
        while (w > target[0] || h > target[1] || !converted) {
            int nextW = Math.max(target[0], w / 2);
            int nextH = Math.max(target[1], h / 2);
            if (w <= target[0] && h <= target[1]) {
                nextW = w;
                nextH = h;
            }
            BufferedImage next = new BufferedImage(nextW, nextH, type);
            java.awt.Graphics2D g = next.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                if (!alpha) {
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, nextW, nextH);
                }
                g.drawImage(current, 0, 0, nextW, nextH, null);
            } finally {
                g.dispose();
            }
            current = next;
            w = nextW;
            h = nextH;
            converted = true;
        }
        return current;
    }

    private static byte[] writeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("no JPEG writer");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param); // no metadata object: nothing of the original is carried over
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) throw new IOException("no PNG writer");
        return bytes.toByteArray();
    }
}
