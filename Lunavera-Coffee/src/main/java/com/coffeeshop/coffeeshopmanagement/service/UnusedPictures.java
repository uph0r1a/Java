package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Finds the pictures in {@code data/images/} that no product points to, and deletes them on request (TODO 4.27).
 *
 * <p>They pile up when a product is deleted while its picture is shared, when a dialog is cancelled after a picture was
 * chosen, or when a restore brings back pictures of products that no longer exist; and every backup carries all of them.
 *
 * <p>Safety rules, because a deleted picture cannot be brought back except from a backup:
 * <ul>
 *   <li>only picture files (png, jpg, jpeg, jfif, jpe, gif, bmp) <b>inside</b> the images folder are ever listed or deleted;</li>
 *   <li>a file changed in the last {@link #MIN_AGE} is left alone: a picture just chosen in an open "add product" window is
 *       copied there before the product is saved;</li>
 *   <li>a product row may hold a bare file name or an old absolute path; both count as "in use";</li>
 *   <li>the delete step looks again and removes only files that are still unused <i>and</i> were in the list the person
 *       confirmed.</li>
 * </ul>
 */
public final class UnusedPictures {

    private static final Logger LOGGER = Logger.getLogger(UnusedPictures.class.getName());

    /** A picture younger than this is never offered for deletion. */
    static final Duration MIN_AGE = Duration.ofHours(1);
    private static final Set<String> PICTURE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "jfif", "jpe", "gif", "bmp");

    public record Picture(Path file, long bytes) { }

    public record Report(List<Picture> unused, long totalBytes) {
        public boolean isEmpty() {
            return unused.isEmpty();
        }
    }

    public record Result(int deleted, long bytesFreed, int failed) { }

    private UnusedPictures() {
    }

    /** Looks at the real images folder and the products of the shop. */
    public static Report scan() {
        List<String> stored = new ArrayList<>();
        for (Product product : new ProductDAO().findAll()) {
            if (product.getImagePath() != null && !product.getImagePath().isBlank()) stored.add(product.getImagePath());
        }
        return scan(DataPaths.imagesDir(), stored, Instant.now());
    }

    /** {@code stored}: the {@code image_path} of every product (inactive ones too). */
    static Report scan(Path imagesDir, Collection<String> stored, Instant now) {
        List<Picture> unused = new ArrayList<>();
        long total = 0;
        if (!Files.isDirectory(imagesDir)) return new Report(unused, 0);
        Set<String> inUse = new HashSet<>();
        for (String value : stored) {
            Path path = referenced(imagesDir, value);
            if (path != null) inUse.add(key(path));
        }
        Instant newestAllowed = now.minus(MIN_AGE);
        try (Stream<Path> walk = Files.walk(imagesDir)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                if (!isPicture(file) || inUse.contains(key(file))) continue;
                try {
                    if (Files.getLastModifiedTime(file).toInstant().isAfter(newestAllowed)) continue;
                    long size = Files.size(file);
                    unused.add(new Picture(file, size));
                    total += size;
                } catch (IOException skipped) {
                    // a file that cannot be read now is not offered
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not look through the images folder", e);
        }
        unused.sort(java.util.Comparator.comparing((Picture p) -> p.file().getFileName().toString()));
        return new Report(unused, total);
    }

    /**
     * Deletes the files of {@code confirmed} that are still unused. Administrators only. Call it after a backup; it looks
     * again, so a product saved since the list was shown keeps its picture.
     */
    public static Result deleteConfirmed(Report confirmed) {
        Permissions.requireAdmin("delete unused pictures");
        return deleteConfirmed(DataPaths.imagesDir(), confirmed, scan());
    }

    static Result deleteConfirmed(Path imagesDir, Report confirmed, Report fresh) {
        Set<String> allowed = new HashSet<>();
        for (Picture p : confirmed.unused()) allowed.add(key(p.file()));
        Path root;
        try {
            root = imagesDir.toRealPath();
        } catch (IOException e) {
            return new Result(0, 0, confirmed.unused().size());
        }
        int deleted = 0, failed = 0;
        long freed = 0;
        for (Picture p : fresh.unused()) {
            if (!allowed.contains(key(p.file()))) continue;
            try {
                if (!p.file().toRealPath().startsWith(root)) continue; // never outside the images folder
                if (Files.deleteIfExists(p.file())) {
                    deleted++;
                    freed += p.bytes();
                }
            } catch (IOException | RuntimeException e) {
                failed++;
            }
        }
        SecurityLog.event(SecurityLog.actor() + " deleted " + deleted + " unused product pictures (" + freed + " bytes)");
        return new Result(deleted, freed, failed);
    }

    private static boolean isPicture(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && PICTURE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** The file a stored value stands for: a bare name lives in the images folder, an old value may be a full path. */
    private static Path referenced(Path imagesDir, String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            boolean hasSeparator = stored.indexOf('/') >= 0 || stored.indexOf('\\') >= 0;
            Path path = Path.of(stored);
            return hasSeparator || path.isAbsolute() ? path : imagesDir.resolve(stored);
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    private static String key(Path path) {
        String text = path.toAbsolutePath().normalize().toString();
        return onWindows() ? text.toLowerCase(Locale.ROOT) : text;
    }

    private static boolean onWindows() {
        return java.io.File.separatorChar == '\\';
    }
}
