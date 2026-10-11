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
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
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
            throw new IOException("Chỉ hỗ trợ ảnh PNG hoặc JPG (tệp \"" + source.getName() + "\" không hợp lệ).");
        }
        Path dir = directory();
        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            String fileName = baseNames.get() + "." + extension;
            try {
                // No REPLACE_EXISTING: Files.copy then fails with FileAlreadyExistsException
                // instead of overwriting another product's picture.
                Files.copy(source.toPath(), dir.resolve(fileName));
                return fileName;
            } catch (FileAlreadyExistsException taken) {
                // Pick another name and try again.
            }
        }
        throw new IOException("Không thể tạo tên tệp ảnh duy nhất. Vui lòng thử lại.");
    }

    /** Lowercase extension without the dot, or an empty string when there is none. */
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

    /** Best-effort delete - never throws. A missing file, an old value that never resolves, or
     *  a locked file on the OS shouldn't block saving/deleting the product record itself. */
    public static void deleteQuietly(String stored) {
        File file = resolve(stored);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException | RuntimeException ignored) {
            // Not worth surfacing to the user.
        }
    }
}
