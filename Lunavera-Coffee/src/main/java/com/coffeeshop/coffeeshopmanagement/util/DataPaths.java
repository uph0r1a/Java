package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The one place that knows where the app keeps its files: the database, the product pictures,
 * the log files and the automatic backups. Everything else asks this class, so moving the data
 * (or pointing a test at a temp folder) is a one-line change.
 *
 * <p>Where the data folder is, in order of priority:
 * <ol>
 *   <li>the {@code -Dlunavera.dataDir=...} system property,</li>
 *   <li>the {@code LUNAVERA_DATA_DIR} environment variable,</li>
 *   <li>otherwise {@code data/} inside the folder the app was started from (the project folder
 *       with {@code mvn javafx:run}).</li>
 * </ol>
 * A relative value is taken relative to the start-up folder. The value is read on every call
 * (not cached), so tests can change it.
 *
 * <p>Older versions kept everything in {@code ~/.lunavera-coffee}; {@link #migrateLegacyDataIfNeeded}
 * copies that data across once.
 */
public final class DataPaths {

    public static final String DATA_DIR_PROPERTY = "lunavera.dataDir";
    public static final String DATA_DIR_ENV = "LUNAVERA_DATA_DIR";
    public static final String DATABASE_FILE_NAME = "lunavera.db";
    static final String MIGRATION_MARKER = ".migrated-from-home";

    private static final Logger LOGGER = Logger.getLogger(DataPaths.class.getName());

    private DataPaths() {
    }

    /** The folder that holds all app data (not created by this call). */
    public static Path dataDir() {
        String configured = System.getProperty(DATA_DIR_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(DATA_DIR_ENV);
        }
        Path base = (configured == null || configured.isBlank())
                ? Path.of(System.getProperty("user.dir"), "data")
                : Path.of(configured.trim());
        return base.toAbsolutePath().normalize();
    }

    public static Path databaseFile() {
        return dataDir().resolve(DATABASE_FILE_NAME);
    }

    public static Path imagesDir() {
        return dataDir().resolve("images");
    }

    public static Path logsDir() {
        return dataDir().resolve("logs");
    }

    /** Hourly SQLite copies of a MariaDB database (see SqliteSnapshotService). */
    public static Path snapshotsDir() {
        return dataDir().resolve("snapshots");
    }

    /**
     * Where short-lived copies go while a backup is made or checked (TODO 2A.21). They hold customer data and password hashes,
     * so they stay inside the owner-only data folder instead of the system temp folder that every user of the PC can list.
     */
    public static Path tmpDir() {
        return dataDir().resolve("tmp");
    }

    /** A new empty file in {@link #tmpDir()} (the folder is made if needed). */
    public static Path createTempFile(String prefix, String suffix) throws IOException {
        Path dir = Files.createDirectories(tmpDir());
        return Files.createTempFile(dir, prefix, suffix);
    }

    /**
     * Removes what a crash left in {@link #tmpDir()}. Called once at start-up, after the single-instance lock is held, so no
     * other copy of the app can be using those files. Never throws.
     *
     * @return how many files were removed
     */
    public static int cleanTmp() {
        Path dir = tmpDir();
        int removed = 0;
        if (!Files.isDirectory(dir)) return 0;
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                try {
                    if (Files.isRegularFile(file) && Files.deleteIfExists(file)) removed++;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not remove a leftover temporary file: " + file, e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not list the temporary folder: " + dir, e);
        }
        return removed;
    }

    public static Path backupsDir() {
        return dataDir().resolve("backups");
    }

    /** Where older versions kept everything: {@code ~/.lunavera-coffee}. */
    public static Path legacyDir() {
        return Path.of(System.getProperty("user.home"), ".lunavera-coffee").toAbsolutePath().normalize();
    }

    /** True if {@code path} is the data folder or anything inside it (e.g. a backup saved next to the live data). */
    public static boolean isInsideDataDir(Path path) {
        return path != null && path.toAbsolutePath().normalize().startsWith(dataDir());
    }

    /**
     * Makes the data folder readable only by the user who runs the app (mode {@code rwx------}), because it holds customer
     * phone numbers, password hashes and backups (TODO 2A.4, 2A.14). Only the folder itself is changed: nothing inside it can be
     * reached by other users while the folder is closed to them. Works on Linux and macOS. On Windows (no POSIX permissions) it
     * does nothing and returns false; there the folder's rights come from the place it sits in (see the README).
     * Never throws: a folder that cannot be changed (not ours, read-only file system) is only logged.
     *
     * @return true if the folder is now owner-only
     */
    public static boolean restrictToOwner(Path dir) {
        if (dir == null) return false;
        try {
            PosixFileAttributeView view = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
            if (view == null || !Files.isDirectory(dir)) return false;
            if (Files.getPosixFilePermissions(dir).equals(PosixFilePermissions.fromString("rwx------"))) return true;
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Could not make the data folder owner-only: " + dir, e);
            return false;
        }
    }

    /**
     * One-time move from {@code ~/.lunavera-coffee} to the data folder. It <b>copies</b> the
     * database, the pictures and the backups and never deletes or changes the old folder, so the
     * old data stays as a safety copy.
     *
     * <p>Runs only when the new folder has no database yet and has not been migrated before (a marker
     * file records it, so deleting the new database later does not silently bring the old one back).
     * Must be called before the first database connection is opened.
     *
     * @return true if data was copied
     * @throws IllegalStateException if the old database exists but could not be copied: starting
     *         with an empty database would look like all the shop's data was lost
     */
    public static boolean migrateLegacyDataIfNeeded() {
        Path legacy = legacyDir();
        Path target = dataDir();
        Path legacyDb = legacy.resolve(DATABASE_FILE_NAME);
        if (legacy.equals(target)
                || Files.exists(target.resolve(MIGRATION_MARKER))
                || Files.exists(target.resolve(DATABASE_FILE_NAME))
                || !Files.isRegularFile(legacyDb)) {
            return false;
        }
        // A journal next to the old database means the old app is still open (or crashed mid-write): a copy now could be
        // inconsistent (TODO 3.40).
        if (Files.exists(legacy.resolve(DATABASE_FILE_NAME + "-journal")) || Files.exists(legacy.resolve(DATABASE_FILE_NAME + "-wal"))) {
            throw new IllegalStateException("Dữ liệu cũ ở " + legacy + " đang được một bản Lunavera cũ sử dụng. "
                    + "Hãy đóng bản cũ rồi mở lại chương trình để chuyển dữ liệu. Dữ liệu cũ vẫn còn nguyên.");
        }
        Path temporary = target.resolve(DATABASE_FILE_NAME + ".copying");
        try {
            Files.createDirectories(target);
            Files.copy(legacyDb, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target.resolve(DATABASE_FILE_NAME), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target.resolve(DATABASE_FILE_NAME));
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // nothing more to do
            }
            throw new IllegalStateException("Không thể chuyển dữ liệu cũ từ " + legacy + " sang " + target
                    + ". Dữ liệu cũ vẫn còn nguyên ở chỗ cũ. Lý do: " + e.getMessage(), e);
        }
        int pictures = copyFolderQuietly(legacy.resolve("images"), target.resolve("images"));
        int backups = copyFolderQuietly(legacy.resolve("backups"), target.resolve("backups"));
        try {
            Files.writeString(target.resolve(MIGRATION_MARKER),
                    "Data copied from " + legacy + " on " + LocalDateTime.now() + "\n"
                            + "The old folder was left untouched.\n",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not write the migration marker file", e);
        }
        LOGGER.info("Copied the existing data from " + legacy + " to " + target + ": database, " + pictures
                + " picture(s), " + backups + " backup(s). The old folder was left in place.");
        return true;
    }

    /** Copies every file under {@code from} into {@code to}, skipping files that already exist there. A failure is logged, not thrown. */
    private static int copyFolderQuietly(Path from, Path to) {
        if (!Files.isDirectory(from)) {
            return 0;
        }
        int copied = 0;
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                Path destination = to.resolve(from.relativize(source).toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else if (!Files.exists(destination)) {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination);
                        copied++;
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not copy " + source + " to the data folder; copy it by hand if needed", e);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not read " + from + " while moving the old data", e);
        }
        return copied;
    }
}
