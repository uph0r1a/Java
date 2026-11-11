package com.coffeeshop.coffeeshopmanagement.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Owns where product images live on disk and what gets stored in {@code products.image_path}.
 *
 * TODO.md item 10a originally stored the *full absolute path* in the database (e.g.
 * {@code /home/alice/.lunavera-coffee/images/172..._latte.png} (the old data folder)), which breaks the moment the
 * database is copied to another machine or another user account - exactly the kind of thing
 * {@link com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig} otherwise takes care to
 * avoid. New saves now store just the file name; {@link #resolve} still understands an old
 * absolute path already sitting in an existing database, so upgrading doesn't orphan anyone's
 * pictures.
 */
public final class ImageStorage {

    private ImageStorage() {
    }

    private static Path directoryPath() {
        return DataPaths.imagesDir();
    }

    public static Path directory() throws IOException {
        Path dir = directoryPath();
        Files.createDirectories(dir);
        return dir;
    }

    /** Picture types that may be stored; anything else is rejected before a byte is copied. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "jfif", "jpe", "gif", "bmp");
    private static final int MAX_NAME_ATTEMPTS = 5;

    /**
     * Copies {@code source} into the managed images directory under a unique, safe name and
     * returns the value to store on the product ({@code Product.setImagePath(...)}) - a bare file
     * name, not a path.
     *
     * <p>The stored name is {@code <millis>-<random UUID>.<extension>}: the original name (spaces,
     * Vietnamese letters, very long names) is dropped, and only a lowercase {@code png}, {@code jpg}
     * or {@code jpeg} extension is kept. The copy never replaces an existing file; if the name is
     * somehow taken, a new one is generated.
     *
     * @throws IOException if the file is not a PNG/JPEG by extension, or cannot be copied
     */
    public static String storeNewFile(File source) throws IOException {
        return storeNewFile(source, () -> System.currentTimeMillis() + "-" + UUID.randomUUID());
    }

    /** Same, with the base name (without extension) supplied by the caller - lets tests force a collision. */
    static String storeNewFile(File source, Supplier<String> baseNames) throws IOException {
        String extension = extensionOf(source.getName());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IOException(I18n.t("image.type", source.getName()));
        }
        // JavaFX reads JFIF/JPE as JPEG; store them under the usual name (TODO 2.58).
        if (extension.equals("jfif") || extension.equals("jpe")) {
            extension = "jpg";
        }
        Path dir = directory();
        // What is stored is not the picked file but a cleaned copy of it: the right way up, at most PictureCleaner.MAX_SIDE pixels on
        // the long side, and none of the original's metadata (the GPS position of a phone photo, TODO 3.73).
        PictureCleaner.Cleaned cleaned = cleaned(source.toPath(), extension);
        extension = cleaned.extension();
        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            String fileName = baseNames.get() + "." + extension;
            try {
                // CREATE_NEW: fails with FileAlreadyExistsException instead of overwriting another product's picture.
                Files.write(dir.resolve(fileName), cleaned.bytes(), java.nio.file.StandardOpenOption.CREATE_NEW);
                return fileName;
            } catch (FileAlreadyExistsException taken) {
                // Pick another name and try again.
            }
        }
        throw new IOException(I18n.t("image.name.fail"));
    }

    /**
     * The bytes to store for a picked file. A picture {@link PictureCleaner} can read is cleaned; one it cannot (an unusual JPEG, a file
     * that only JavaFX opens, or one with more pixels than we decode) is kept as it is, but a JPEG still loses its Exif, XMP, IPTC and
     * comment segments, which is where the position is. Anything else that is not a picture is copied, as before.
     */
    static PictureCleaner.Cleaned cleaned(Path source, String extension) throws IOException {
        byte[] original = Files.readAllBytes(source);
        boolean jpeg = extension.equals("jpg") || extension.equals("jpeg");
        ImageSize.Size size = ImageSize.read(source);
        if (size == null || size.pixels() <= MAX_PICTURE_PIXELS) {
            PictureCleaner.Cleaned made = PictureCleaner.clean(original, extension);
            if (made != null) return made;
        }
        return new PictureCleaner.Cleaned(jpeg ? JpegMetadata.withoutMetadata(original) : original, extension);
    }

    /** Lowercase extension without the dot, or an empty string when there is none. */
    /** Pictures above this size are refused: the list decodes every picture and a huge one makes the app slow (TODO 3.12). */
    public static final long MAX_PICTURE_BYTES = 8L * 1024 * 1024;

    /** The most pixels a picture may have (16 million, about 4,000 x 4,000): decoded it needs 4 bytes each, so 64 MB (TODO 3.43). */
    public static final long MAX_PICTURE_PIXELS = 16_000_000L;
    /** The longest side of a picture; stops a one-pixel-high strip of enormous width that stays under the pixel count. */
    public static final int MAX_PICTURE_SIDE = 8_000;

    /** The checks that need no picture library: present, not empty, not too many bytes, not too many pixels (TODO 3.43). */
    static String sizeProblem(File file) {
        if (file == null || !file.isFile()) return I18n.t("image.unreadable");
        long size = file.length();
        if (size == 0) return I18n.t("image.empty");
        if (size > MAX_PICTURE_BYTES) {
            return I18n.t("image.toolarge", (size / 1024 / 1024), (MAX_PICTURE_BYTES / 1024 / 1024));
        }
        ImageSize.Size pixels = ImageSize.read(file.toPath());
        if (pixels == null) return I18n.t("image.invalid"); // not a picture of a known type, or a cut-off one
        if (pixels.pixels() > MAX_PICTURE_PIXELS || pixels.width() > MAX_PICTURE_SIDE || pixels.height() > MAX_PICTURE_SIDE) {
            return I18n.t("image.toomanypixels", pixels.width(), pixels.height());
        }
        return null;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1
                ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Turns a stored value (new-style bare file name, or an old-style absolute path from
     *  before this fix) into the real file, or null if it can't be resolved / doesn't exist.
     *  Read-only: unlike {@link #directory()}, never creates the images directory. */
    public static File resolve(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        File direct = new File(stored);
        // An old absolute/relative path (contains a separator, or is itself already absolute)
        // is used as-is; a bare file name is resolved against the managed directory.
        File candidate = (stored.indexOf('/') >= 0 || stored.indexOf('\\') >= 0 || direct.isAbsolute())
                ? direct
                : directoryPath().resolve(stored).toFile();
        return candidate.exists() ? candidate : null;
    }

    /** True when {@code file} really lies inside the managed images folder (symbolic links and ".." resolved). */
    static boolean isInsideImagesFolder(File file) {
        try {
            Path dir = directoryPath().toRealPath();
            return file.toPath().toRealPath().startsWith(dir);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Best-effort delete - never throws. A missing file, an old value that never resolves, or
     *  a locked file on the OS shouldn't block saving/deleting the product record itself.
     *  Only files inside the managed images folder are ever deleted: an old product row that holds a full path to
     *  somebody's own photo elsewhere on the disk leaves that photo alone (TODO 2.18). */
    public static void deleteQuietly(String stored) {
        File file = resolve(stored);
        if (file == null || !isInsideImagesFolder(file)) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException | RuntimeException ignored) {
            // Not worth surfacing to the user.
        }
    }
}
