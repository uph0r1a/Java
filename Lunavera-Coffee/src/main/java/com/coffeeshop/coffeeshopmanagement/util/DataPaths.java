package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
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
 * <p>Where the data folder is, in order of priority (TODO 3.69: it used to be {@code data/} inside whatever folder the app was
 * started from, so a second shortcut with another "Start in" folder opened a second, empty shop):
 * <ol>
 *   <li>the {@code -Dlunavera.dataDir=...} system property,</li>
 *   <li>the {@code LUNAVERA_DATA_DIR} environment variable,</li>
 *   <li>{@code data/} inside the start-up folder, but only when it already holds a shop (a {@code lunavera.db} or a
 *       {@code database.properties}): an install made before this rule, or the project folder with {@code mvn javafx:run},</li>
 *   <li>the folder that held a shop the last time the app ran on this computer (a one-line file in the user's home,
 *       {@link #rememberCurrent()}),</li>
 *   <li>otherwise a fixed folder of this user: {@code %LOCALAPPDATA%\Lunavera} on Windows,
 *       {@code ~/Library/Application Support/Lunavera} on macOS, {@code ~/.local/share/lunavera} elsewhere.</li>
 * </ol>
 * So the start-up folder never makes a new, empty shop next to a shop that exists. A relative value in 1 or 2 is taken relative
 * to the start-up folder. The settings are read on every call, so tests can change them; the choice among 3 to 5 is made once per run.
 *
 * <p>Older versions kept everything in {@code ~/.lunavera-coffee}; {@link #migrateLegacyDataIfNeeded}
 * copies that data across once.
 */
public final class DataPaths {

    public static final String DATA_DIR_PROPERTY = "lunavera.dataDir";
    public static final String DATA_DIR_ENV = "LUNAVERA_DATA_DIR";
    public static final String DATABASE_FILE_NAME = "lunavera.db";
    static final String MIGRATION_MARKER = ".migrated-from-home";
    /** The one-line file in the user's home that names the data folder the app used last (TODO 3.69). */
    public static final String LOCATION_FILE = ".lunavera-data-folder";

    private static final Logger LOGGER = Logger.getLogger(DataPaths.class.getName());

    private DataPaths() {
    }

    /** The folder that holds all app data (not created by this call). */
    public static Path dataDir() {
        String configured = System.getProperty(DATA_DIR_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(DATA_DIR_ENV);
        }
        Path base = (configured == null || configured.isBlank()) ? defaultDir() : Path.of(configured.trim());
        return base.toAbsolutePath().normalize();
    }

    private static volatile Path chosenDefault;

    /**
     * The folder used when neither the property nor the environment variable is set (the list above, 3 to 5). Decided on the first
     * call and kept for the run, so the folder cannot change under the program while it works.
     */
    public static Path defaultDir() {
        Path chosen = chosenDefault;
        if (chosen == null) {
            chosen = chooseDefault(Path.of(System.getProperty("user.dir"), "data").toAbsolutePath().normalize(), rememberedDir(),
                    perUserDir(System.getProperty("os.name", ""), System.getProperty("user.home"), System.getenv("LOCALAPPDATA"),
                            System.getenv("XDG_DATA_HOME")));
            chosenDefault = chosen;
        }
        return chosen;
    }

    /** For tests: forget the choice made by {@link #defaultDir()}. */
    public static void forgetDefaultForTests() {
        chosenDefault = null;
    }

    /** The choice itself, without reading the machine: a shop in the start-up folder, else the remembered one, else the fixed folder. */
    static Path chooseDefault(Path startUpData, Path remembered, Path perUser) {
        if (startUpData != null && holdsShop(startUpData)) return startUpData;
        if (remembered != null && holdsShop(remembered)) return remembered;
        return perUser.toAbsolutePath().normalize();
    }

    /** True when {@code dir} holds a shop: a SQLite database, or the settings of a MariaDB/MySQL server (which has no database file). */
    public static boolean holdsShop(Path dir) {
        return dir != null && (Files.isRegularFile(dir.resolve(DATABASE_FILE_NAME)) || Files.isRegularFile(dir.resolve("database.properties")));
    }

    /** The fixed per-user folder for the data, by system (parameters instead of reading the machine, so it can be tested). */
    static Path perUserDir(String osName, String home, String localAppData, String xdgDataHome) {
        String os = osName == null ? "" : osName.toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            Path base = localAppData != null && !localAppData.isBlank() ? Path.of(localAppData) : Path.of(home, "AppData", "Local");
            return base.resolve("Lunavera");
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", "Lunavera");
        Path base = xdgDataHome != null && !xdgDataHome.isBlank() ? Path.of(xdgDataHome) : Path.of(home, ".local", "share");
        return base.resolve("lunavera");
    }

    private static Path locationFile() {
        return Path.of(System.getProperty("user.home")).resolve(LOCATION_FILE);
    }

    /** The data folder the app used the last time it ran on this computer, or null (no file, unreadable, not an absolute path). */
    public static Path rememberedDir() {
        try {
            Path file = locationFile();
            if (!Files.isRegularFile(file)) return null;
            String line = Files.readString(file).trim();
            if (line.isEmpty() || line.indexOf('\n') >= 0) return null;
            Path dir = Path.of(line);
            return dir.isAbsolute() ? dir.normalize() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Writes the folder in use into the file in the user's home, when it holds a shop, so the next start finds the same shop from
     * any shortcut. Called by the app once the database is open (never by tests). Never throws.
     *
     * @return true if the file names the folder in use afterwards
     */
    public static boolean rememberCurrent() {
        Path dir = dataDir();
        if (!holdsShop(dir)) return false;
        if (dir.equals(rememberedDir())) return true;
        Path file = locationFile();
        Path temporary = file.resolveSibling(LOCATION_FILE + ".tmp");
        try {
            Files.writeString(temporary, dir + System.lineSeparator());
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not remember the data folder in " + file, e);
            return false;
        }
    }

    /**
     * Call before anything is written to the data folder: when the folder in use holds no shop yet but the folder remembered from
     * the last run does (the program was pointed at an empty folder by {@code -Dlunavera.dataDir} or {@code LUNAVERA_DATA_DIR}),
     * returns that other folder so the person can be asked before a new, empty shop is made; otherwise null.
     */
    public static Path existingShopElsewhere() {
        Path now = dataDir();
        if (holdsShop(now)) return null;
        Path remembered = rememberedDir();
        return remembered != null && !remembered.equals(now) && holdsShop(remembered) ? remembered : null;
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
     * Makes the data folder readable only by the user who runs the app, because it holds customer phone numbers, password hashes
     * and backups (TODO 2A.4, 2A.14). On Linux and macOS the folder gets mode {@code rwx------}; only the folder itself is changed,
     * because nothing inside can be reached while the folder is closed. On Windows (no POSIX rights) the folder's access list is
     * replaced by one entry that gives the owner full control and is inherited by everything inside, and the files and folders
     * already inside get the same list (Windows would otherwise keep the rights they inherited from the parent folder). Other users
     * and the "Users" group lose access; an administrator can still take ownership.
     * Never throws: a folder that cannot be changed (not ours, read-only file system) is only logged.
     *
     * @return true if the folder is now owner-only
     */
    public static boolean restrictToOwner(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return false;
        try {
            PosixFileAttributeView view = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
            if (view != null) {
                if (Files.getPosixFilePermissions(dir).equals(PosixFilePermissions.fromString("rwx------"))) return true;
                Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
                return true;
            }
            AclFileAttributeView acl = Files.getFileAttributeView(dir, AclFileAttributeView.class);
            if (acl == null) return false;
            return restrictWithAcl(dir);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Could not make the data folder owner-only: " + dir, e);
            return false;
        }
    }

    /** The one access entry of an owner-only folder on Windows: the owner, everything allowed, passed on to what is inside. */
    static List<AclEntry> ownerOnlyAcl(UserPrincipal owner) {
        return List.of(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
                .build());
    }

    /** Windows way of {@link #restrictToOwner}: the folder first, then what is already inside it (a failure inside is only logged). */
    private static boolean restrictWithAcl(Path dir) throws IOException {
        UserPrincipal owner = Files.getOwner(dir);
        List<AclEntry> wanted = ownerOnlyAcl(owner);
        AclFileAttributeView view = Files.getFileAttributeView(dir, AclFileAttributeView.class);
        if (!wanted.equals(view.getAcl())) view.setAcl(wanted);
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path inside : (Iterable<Path>) walk.skip(1)::iterator) {
                try {
                    AclFileAttributeView v = Files.getFileAttributeView(inside, AclFileAttributeView.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    if (v == null) continue;
                    // a file gets the list without the inherit flags, which only mean something on a folder
                    List<AclEntry> forThis = Files.isDirectory(inside, java.nio.file.LinkOption.NOFOLLOW_LINKS) ? wanted
                            : List.of(AclEntry.newBuilder(wanted.get(0)).setFlags().build());
                    if (!forThis.equals(v.getAcl())) v.setAcl(forThis);
                } catch (IOException | RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Could not close this file to other users: " + inside, e);
                }
            }
        }
        return true;
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
            throw new IllegalStateException(I18n.t("data.legacy.inuse", legacy));
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
            throw new IllegalStateException(I18n.t("data.legacy.movefail", legacy, target, e.getMessage()), e);
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
