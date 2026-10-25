package com.coffeeshop.coffeeshopmanagement.service;

import java.util.Locale;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backups of everything the shop saves (TODO 2.8). Before this the backup button copied only the database file:
 * product pictures were not in it, nothing ran on a schedule, old copies piled up, and restoring meant replacing a file by hand.
 *
 * <ul>
 *   <li>A backup is one {@code .zip}: {@code lunavera.db} (a consistent copy made with {@code VACUUM INTO}), the
 *       {@code images/} folder, the transfer QR picture if there is one, and a small {@code backup-info.txt}.</li>
 *   <li>It is written to a temporary name and moved into place at the end, so a failed backup never destroys an older good one.</li>
 *   <li>{@link #autoBackupIfDue()} makes one automatically when the newest automatic backup is older than a day and keeps only
 *       the latest {@value #KEEP_AUTOMATIC}; it only ever deletes files it made itself ({@code auto-*.zip}).</li>
 *   <li>Restoring cannot replace a database that is open, so {@link #scheduleRestore} checks the backup and parks it, and
 *       {@link #applyPendingRestore()} swaps it in at the next start-up, after saving the current state as
 *       {@code before-restore-*.zip}. Older plain {@code .db} backups restore the database only.</li>
 * </ul>
 */
public final class BackupService {

    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());

    public static final int KEEP_AUTOMATIC = 14;
    /** How many {@code before-restore-*.zip} safety copies are kept (TODO 4.28); each holds a full copy of the pictures. */
    public static final int KEEP_BEFORE_RESTORE = 3;
    /**
     * Only every Nth automatic backup carries the pictures (TODO 4.28): they hardly compress, so 14 copies of a big
     * {@code images/} folder were what filled the disk. The others are named {@code auto-<time>-db.zip} and say so in
     * {@code backup-info.txt}; a restore from one of them keeps the pictures that are in the shop now.
     */
    public static final int PICTURES_EVERY_NTH_AUTOMATIC = 7;
    static final String NO_PICTURES_SUFFIX = "-db";
    static final String NO_PICTURES_LINE = "pictures: not included (kept in the newest earlier backup that has them)";
    public static final Duration AUTOMATIC_EVERY = Duration.ofHours(24);
    static final String AUTO_PREFIX = "auto-";
    static final String BEFORE_RESTORE_PREFIX = "before-restore-";
    /** The copy made right before unused pictures are deleted (TODO 4.27); the newest {@link #KEEP_BEFORE_RESTORE} are kept. */
    static final String BEFORE_CLEANUP_PREFIX = "before-cleanup-";
    static final String PENDING_ZIP = "restore-pending.zip";
    static final String PENDING_DB = "restore-pending.db";
    static final String DB_ENTRY = DataPaths.DATABASE_FILE_NAME;
    static final String IMAGES_FOLDER = "images";
    static final String INFO_ENTRY = "backup-info.txt";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** What to tell someone who tries the in-app restore while the shop runs on MariaDB/MySQL. */
    public static String mariaDbRestoreHelp() {
        return I18n.t("backup.mariadb.help");
    }

    public enum RestoreOutcome { NOTHING_PENDING, RESTORED, FAILED }

    public record RestoreResult(RestoreOutcome outcome, String message) { }

    private BackupService() {
    }

    // ------------------------------------------------------------------ making backups

    /** Adds ".zip" when the name has no extension, so a typed name always gives a usable file (TODO 2.23). */
    public static Path withZipExtension(Path target) {
        String name = target.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".zip") ? target : target.resolveSibling(name + ".zip");
    }

    /** Writes a full backup (database + pictures) to {@code target}; returns the file actually written. */
    public static Path createBackup(Path target) throws SQLException, IOException {
        return createBackup(target, true);
    }

    /** {@code withPictures} false: database and transfer QR only (automatic backups between two full ones, TODO 4.28). */
    static Path createBackup(Path target, boolean withPictures) throws SQLException, IOException {
        Path zip = withZipExtension(target).toAbsolutePath().normalize();
        // The target always ends in .zip, so it can never be the live lunavera.db (the 2.23 problem of a backup
        // that deleted the database it was meant to copy).
        Path folder = zip.getParent();
        if (zip.startsWith(DataPaths.imagesDir().toAbsolutePath().normalize())) {
            throw new IOException(I18n.t("backup.dir.pictures"));
        }
        Files.createDirectories(folder);
        if (!Files.isWritable(folder)) {
            throw new IOException(I18n.t("backup.dir.notwritable", folder));
        }
        long needed = sizeOf(DataPaths.databaseFile()) + (withPictures ? sizeOf(DataPaths.imagesDir()) : 0);
        if (Files.getFileStore(folder).getUsableSpace() < needed + 1_000_000L) {
            throw new IOException(I18n.t("backup.dir.nospace", folder));
        }
        Path databaseCopy = DataPaths.createTempFile("lunavera-backup", DatabaseConfig.backupExtension());
        Path partial = zip.resolveSibling(zip.getFileName() + ".part");
        try {
            DatabaseConfig.backupTo(databaseCopy);
            writeZip(partial, databaseCopy, databaseEntryName(), DataPaths.imagesDir(), DataPaths.dataDir(), withPictures);
            // Only now does the new file replace an older one of the same name.
            Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(databaseCopy);
            Files.deleteIfExists(partial);
        }
        verifyOrDelete(zip);
        return zip;
    }

    /**
     * Reads the backup that was just written back (TODO 3.63): a failing disk or a pulled USB stick can leave a zip that looks
     * fine in the folder, and the first sign would be the restore that fails. A copy that does not pass is deleted and
     * reported, so a bad file is never kept as if it were a backup.
     */
    private static void verifyOrDelete(Path zip) throws IOException {
        try {
            verifyZip(zip, DatabaseConfig.isSqlite());
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(zip);
            throw new IOException(I18n.t("backup.verify.failed", e.getMessage()), e);
        }
    }

    /**
     * The check itself: the zip's index must be readable (a cut-off file has none), every entry is read to its end so its CRC is
     * checked, and for a SQLite shop the database inside is opened and put through {@code integrity_check} ({@link #inspect}).
     * A MariaDB backup holds a text dump instead, so only the zip itself can be checked.
     */
    static void verifyZip(Path zip, boolean sqlite) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (java.util.zip.ZipFile file = new java.util.zip.ZipFile(zip.toFile())) {
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                try (InputStream in = file.getInputStream(entry)) {
                    while (in.read(buffer) != -1) {
                        // reading to the end is the check
                    }
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException(I18n.t("backup.corrupt.zip", e.getMessage()), e);
        }
        if (sqlite) {
            inspect(zip);
        }
    }

    /** The extension of a backup locked with a passphrase (TODO 2A.4). */
    public static final String ENCRYPTED_EXTENSION = ".lvbak";


    /** Adds ".lvbak" when the name does not end with it, so a typed name always gives the right kind of file. */
    public static Path withEncryptedExtension(Path target) {
        String name = target.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(ENCRYPTED_EXTENSION)) return target;
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
        return target.resolveSibling(name + ENCRYPTED_EXTENSION);
    }

    /**
     * Writes a full backup like {@link #createBackup(Path)} and locks it with {@code passphrase} (TODO 2A.4): the file
     * can be lost on a USB stick without its customer names, phone numbers and password hashes being readable. The
     * plain zip exists only for a moment in the program's own temp folder and is always deleted.
     */
    public static Path createEncryptedBackup(Path target, char[] passphrase) throws SQLException, IOException {
        String problem = BackupCrypto.passphraseProblem(passphrase);
        if (problem != null) throw new IOException(problem);
        Path out = withEncryptedExtension(target).toAbsolutePath().normalize();
        if (out.startsWith(DataPaths.imagesDir().toAbsolutePath().normalize())) {
            throw new IOException(I18n.t("backup.dir.pictures"));
        }
        Path folder = out.getParent();
        Files.createDirectories(folder);
        if (!Files.isWritable(folder)) {
            throw new IOException(I18n.t("backup.dir.notwritable", folder));
        }
        Path plain = DataPaths.createTempFile("lunavera-plain-backup", ".zip");
        Path partial = out.resolveSibling(out.getFileName() + ".part");
        try {
            createBackup(plain);
            if (Files.getFileStore(folder).getUsableSpace() < Files.size(plain) + 1_000_000L) {
                throw new IOException(I18n.t("backup.dir.nospace", folder));
            }
            BackupCrypto.encrypt(plain, partial, passphrase);
            Files.move(partial, out, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(plain);
            Files.deleteIfExists(partial);
        }
        // The plain zip was checked by createBackup; this reads the locked file back and opens it with the passphrase (TODO 3.63).
        Path opened = null;
        try {
            opened = decryptToTemp(out, passphrase);
            verifyZip(opened, DatabaseConfig.isSqlite());
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(out);
            throw new IOException(I18n.t("backup.verify.failed", e.getMessage()), e);
        } finally {
            if (opened != null) Files.deleteIfExists(opened);
        }
        return out;
    }

    /** True when {@code source} is a backup locked with a passphrase. */
    public static boolean isEncryptedBackup(Path source) {
        return BackupCrypto.isEncrypted(source);
    }

    /** Opens a locked backup into a temporary zip; the caller deletes it. A wrong passphrase gives a clear message. */
    private static Path decryptToTemp(Path source, char[] passphrase) throws IOException {
        Path plain = DataPaths.createTempFile("lunavera-decrypted", ".zip");
        try {
            BackupCrypto.decrypt(source, plain, passphrase);
            return plain;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(plain);
            throw e;
        }
    }

    /** Total size in bytes of a file or of every file under a folder; 0 when it does not exist. */
    private static long sizeOf(Path path) {
        try {
            if (Files.isRegularFile(path)) return Files.size(path);
            if (!Files.isDirectory(path)) return 0;
            try (Stream<Path> files = Files.walk(path)) {
                return files.filter(Files::isRegularFile).mapToLong(f -> {
                    try { return Files.size(f); } catch (IOException e) { return 0; }
                }).sum();
            }
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * The name of the database file inside a backup zip: {@code lunavera.db} for SQLite, {@code lunavera.sql} (a mysqldump text
     * file, readable by phpMyAdmin's "Import") for MariaDB/MySQL.
     */
    static String databaseEntryName(boolean sqlite) {
        return sqlite ? DB_ENTRY : "lunavera.sql";
    }

    private static String databaseEntryName() {
        return databaseEntryName(DatabaseConfig.isSqlite());
    }

    /** SQLite-only restore flow: the safety copy before a restore is always a SQLite zip. */
    private static void writeZip(Path zipFile, Path databaseFile, Path imagesDir, Path dataDir) throws IOException {
        writeZip(zipFile, databaseFile, DB_ENTRY, imagesDir, dataDir, true);
    }

    private static void writeZip(Path zipFile, Path databaseFile, String databaseEntry, Path imagesDir, Path dataDir,
                                 boolean withPictures) throws IOException {
        try (OutputStream out = Files.newOutputStream(zipFile);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(INFO_ENTRY));
            zip.write(("Lunavera Coffee backup\ncreated: " + LocalDateTime.now() + "\ndatabase: "
                    + (DB_ENTRY.equals(databaseEntry) ? "SQLite (" + DB_ENTRY + ")" : "MariaDB/MySQL dump (" + databaseEntry + ")")
                    + (withPictures ? "" : "\n" + NO_PICTURES_LINE)
                    + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(databaseEntry));
            Files.copy(databaseFile, zip);
            zip.closeEntry();
            if (withPictures && Files.isDirectory(imagesDir)) {
                try (Stream<Path> files = Files.walk(imagesDir)) {
                    for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                        String name = IMAGES_FOLDER + "/" + imagesDir.relativize(file).toString().replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(name));
                        Files.copy(file, zip);
                        zip.closeEntry();
                    }
                }
            }
            if (Files.isDirectory(dataDir)) {
                try (DirectoryStream<Path> qr = Files.newDirectoryStream(dataDir, "qr-transfer.*")) {
                    for (Path file : qr) {
                        zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                        Files.copy(file, zip);
                        zip.closeEntry();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ automatic backups

    /**
     * Makes an automatic backup in {@link DataPaths#backupsDir()} when none exists yet or the newest is older than
     * {@link #AUTOMATIC_EVERY}, then prunes. Never throws: a failed automatic backup is logged and the app carries on.
     */
    public static Optional<Path> autoBackupIfDue() {
        try {
            Path dir = DataPaths.backupsDir();
            if (!isDue(dir, Instant.now())) {
                SecondBackupFolder.catchUp(); // a stick plugged in since the last backup gets the newest copy now (TODO 4.35)
                return Optional.empty();
            }
            boolean withPictures = picturesDue(dir, Instant.now());
            Path target = dir.resolve(AUTO_PREFIX + STAMP.format(LocalDateTime.now()) + (withPictures ? "" : NO_PICTURES_SUFFIX) + ".zip");
            Path made = createBackup(target, withPictures);
            pruneAutomatic(dir, KEEP_AUTOMATIC);
            pruneBeforeRestoreQuietly(dir);
            LOGGER.info("Automatic backup written: " + made);
            SecondBackupFolder.catchUp(); // the second place, if this till has one (TODO 4.35); never throws
            return Optional.of(made);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Automatic backup failed", e);
            return Optional.empty();
        }
    }

    /**
     * True when the next automatic backup must carry the pictures: none of the automatic backups has them yet, or the
     * newest one that has them is {@value #PICTURES_EVERY_NTH_AUTOMATIC} backups back (TODO 4.28). Backups with a wrong
     * (future) date are not counted. With 14 kept and a picture copy every 7th, at least two copies with pictures always remain.
     */
    static boolean picturesDue(Path dir, Instant now) throws IOException {
        Instant latestBelievable = now.plus(CLOCK_TOLERANCE);
        List<Path> believable = new ArrayList<>();
        for (Path file : automaticBackups(dir)) {
            if (!stampIsAfter(file, latestBelievable)) believable.add(file);
        }
        believable.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        int withoutSince = 0;
        for (Path file : believable) {
            if (!file.getFileName().toString().endsWith(NO_PICTURES_SUFFIX + ".zip")) {
                return withoutSince >= PICTURES_EVERY_NTH_AUTOMATIC - 1;
            }
            withoutSince++;
        }
        return true;
    }

    /** False for a zip that says in its {@code backup-info.txt} that the pictures were left out; true for every other zip. */
    static boolean picturesIncluded(Path zipFile) {
        try (InputStream in = Files.newInputStream(zipFile); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().equals(INFO_ENTRY)) continue;
                byte[] raw = zip.readNBytes(MAX_INFO_BYTES + 1);
                for (String line : new String(raw, java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    if (line.startsWith("pictures: not included")) return false;
                }
                return true;
            }
        } catch (IOException | RuntimeException e) {
            // unreadable: treated as a normal backup; the restore's own checks report a real problem
        }
        return true;
    }

    /** How far ahead of the clock a file's time may be before it is taken to come from a wrong date (TODO 3.65). */
    static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(10);

    static boolean isDue(Path dir, Instant now) throws IOException {
        Optional<Instant> newest = newestAutomatic(dir, now);
        return newest.isEmpty() || Duration.between(newest.get(), now).compareTo(AUTOMATIC_EVERY) >= 0;
    }

    /**
     * The newest automatic backup with a believable time. A copy made while the clock was set ahead has a time in the future;
     * counting it would make every backup "not due" until the real date caught up, so it is ignored here (TODO 3.65).
     */
    private static Optional<Instant> newestAutomatic(Path dir, Instant now) throws IOException {
        Instant newest = null;
        Instant latestBelievable = now.plus(CLOCK_TOLERANCE);
        for (Path file : automaticBackups(dir)) {
            Instant modified = Files.getLastModifiedTime(file).toInstant();
            if (modified.isAfter(latestBelievable)) continue;
            if (newest == null || modified.isAfter(newest)) newest = modified;
        }
        return Optional.ofNullable(newest);
    }

    private static List<Path> automaticBackups(Path dir) throws IOException {
        return zipsWithPrefix(dir, AUTO_PREFIX);
    }

    private static List<Path> zipsWithPrefix(Path dir, String prefix) throws IOException {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(dir)) return found;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, prefix + "*.zip")) {
            stream.forEach(found::add);
        }
        return found;
    }

    /** Deletes the oldest automatic backups beyond {@code keep}. Touches only {@code auto-*.zip}. @return how many were deleted */
    static int pruneAutomatic(Path dir, int keep) throws IOException {
        return pruneAutomatic(dir, keep, Instant.now());
    }

    static int pruneAutomatic(Path dir, int keep, Instant now) throws IOException {
        return pruneNewest(zipsWithPrefix(dir, AUTO_PREFIX), keep, now);
    }

    /**
     * Deletes the oldest {@code before-restore-*.zip} safety copies beyond {@code keep} (TODO 4.28). Touches only those
     * files: automatic backups, hand-made zips and {@code before-money-migration.db} are left alone.
     * @return how many were deleted
     */
    static int pruneBeforeRestore(Path dir, int keep) throws IOException {
        return pruneNewest(zipsWithPrefix(dir, BEFORE_RESTORE_PREFIX), keep, Instant.now());
    }

    /** A full backup in the backups folder, named {@code before-cleanup-<time>.zip}, taken before pictures are deleted (TODO 4.27). */
    public static Path createBeforeCleanupBackup() throws SQLException, IOException {
        Path dir = DataPaths.backupsDir();
        Path made = createBackup(dir.resolve(BEFORE_CLEANUP_PREFIX + STAMP.format(LocalDateTime.now()) + ".zip"), true);
        pruneNewest(zipsWithPrefix(dir, BEFORE_CLEANUP_PREFIX), KEEP_BEFORE_RESTORE, Instant.now());
        return made;
    }

    /** Same, but a problem is only logged: a restore or a backup must never fail because of tidying. */
    private static void pruneBeforeRestoreQuietly(Path dir) {
        try {
            pruneBeforeRestore(dir, KEEP_BEFORE_RESTORE);
            pruneNewest(zipsWithPrefix(dir, BEFORE_CLEANUP_PREFIX), KEEP_BEFORE_RESTORE, Instant.now());
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not tidy old before-restore copies", e);
        }
    }

    /**
     * Names carry the time, so they decide the order, newest first. A name whose time is in the future (a backup made while the
     * clock was wrong) ranks as the oldest and goes first when there are too many, so one wrong date cannot make a file live
     * forever while the real recent copies are deleted (TODO 3.65).
     */
    private static int pruneNewest(List<Path> all, int keep, Instant now) throws IOException {
        Instant latestBelievable = now.plus(CLOCK_TOLERANCE);
        all.sort(Comparator.comparing((Path p) -> (stampIsAfter(p, latestBelievable) ? "0" : "1") + p.getFileName()).reversed());
        int deleted = 0;
        for (int i = keep; i < all.size(); i++) {
            if (Files.deleteIfExists(all.get(i))) deleted++;
        }
        return deleted;
    }

    /** True when the time written in a backup's name ({@code auto-yyyyMMdd-HHmmss.zip}) is later than {@code limit}; false when it cannot be read. */
    private static boolean stampIsAfter(Path file, Instant limit) {
        String name = file.getFileName().toString();
        for (String prefix : new String[]{AUTO_PREFIX, BEFORE_RESTORE_PREFIX, BEFORE_CLEANUP_PREFIX}) {
            if (name.startsWith(prefix) && name.length() >= prefix.length() + 15) {
                try {
                    LocalDateTime stamp = LocalDateTime.parse(name.substring(prefix.length(), prefix.length() + 15), STAMP);
                    return stamp.atZone(java.time.ZoneId.systemDefault()).toInstant().isAfter(limit);
                } catch (java.time.format.DateTimeParseException e) {
                    return false;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ restoring

    /** True when a restore has been prepared and will be applied at the next start-up. */
    public static boolean restorePending() {
        return Files.exists(DataPaths.dataDir().resolve(PENDING_ZIP)) || Files.exists(DataPaths.dataDir().resolve(PENDING_DB));
    }

    /**
     * Checks that {@code source} is a usable backup (a {@code .zip} from this app, or an old plain {@code .db}) and parks it so
     * the next start-up restores it. Does not touch the live data.
     *
     * @throws IOException with a plain-language message when the file is not a usable backup
     */
    public static Summary scheduleRestore(Path source) throws IOException {
        com.coffeeshop.coffeeshopmanagement.util.Permissions.requireAdmin("restore a backup");
        if (!DatabaseConfig.isSqlite()) {
            throw new IOException(mariaDbRestoreHelp());
        }
        Path check = DataPaths.createTempFile("lunavera-restore-check", ".db");
        try {
            Summary summary = inspectInto(source, check, new Limits(MAX_DATABASE_BYTES, MAX_PICTURE_BYTES, MAX_PICTURES_TOTAL_BYTES));
            boolean zip = isZip(source);
            Path dataDir = DataPaths.dataDir();
            Files.createDirectories(dataDir);
            Files.deleteIfExists(dataDir.resolve(PENDING_ZIP));
            Files.deleteIfExists(dataDir.resolve(PENDING_DB));
            Files.copy(source, dataDir.resolve(zip ? PENDING_ZIP : PENDING_DB), StandardCopyOption.REPLACE_EXISTING);
            return summary;
        } finally {
            Files.deleteIfExists(check);
        }
    }

    /** As {@link #scheduleRestore(Path)}, and also opens a backup locked with a passphrase (TODO 2A.4). */
    public static Summary scheduleRestore(Path source, char[] passphrase) throws IOException {
        if (!BackupCrypto.isEncrypted(source)) return scheduleRestore(source);
        com.coffeeshop.coffeeshopmanagement.util.Permissions.requireAdmin("restore a backup");
        Path plain = decryptToTemp(source, passphrase);
        try {
            return scheduleRestore(plain);
        } finally {
            Files.deleteIfExists(plain);
        }
    }

    /** As {@link #inspect(Path)}, and also opens a backup locked with a passphrase (TODO 2A.4). */
    public static Summary inspect(Path source, char[] passphrase) throws IOException {
        if (!BackupCrypto.isEncrypted(source)) return inspect(source);
        Path plain = decryptToTemp(source, passphrase);
        try {
            return inspect(plain);
        } finally {
            Files.deleteIfExists(plain);
        }
    }

    private static boolean isZip(Path source) {
        return source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /** What a backup holds, shown before the person confirms a restore (TODO 5.22). */
    public record Summary(String created, int orders, String newestOrder, int products, int customers, int pictures,
                          boolean picturesIncluded) {
        public String describe() {
            return (picturesIncluded ? "" : I18n.t("backup.nopictures") + "\n") + I18n.t("backup.describe", (created == null ? I18n.t("startup.unknown") : created), orders, (newestOrder == null ? "" : " " + I18n.t("backup.newestorder", newestOrder)), products, customers, pictures);
        }
    }

    /** Validates a backup (without scheduling anything) and describes it. */
    public static Summary inspect(Path source) throws IOException {
        return inspect(source, new Limits(MAX_DATABASE_BYTES, MAX_PICTURE_BYTES, MAX_PICTURES_TOTAL_BYTES));
    }

    /** The size caps a look at a backup keeps to (TODO 2A.30, 2A.40); a parameter so a test can use small ones. */
    record Limits(long database, long picture, long picturesTotal) { }

    /** Largest {@code backup-info.txt} that is read: it is a few lines, so anything bigger is not one. */
    static final int MAX_INFO_BYTES = 16 * 1024;

    static Summary inspect(Path source, Limits limits) throws IOException {
        Path check = DataPaths.createTempFile("lunavera-inspect", ".db");
        try {
            return inspectInto(source, check, limits);
        } finally {
            Files.deleteIfExists(check);
        }
    }

    private static Summary inspectInto(Path source, Path databaseCopy, Limits limits) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException(I18n.t("backup.notfound"));
        }
        if (BackupCrypto.isEncrypted(source)) {
            throw new IOException(I18n.t("backup.locked.needpass"));
        }
        String created = null;
        int pictures = 0;
        boolean picturesIncluded = true;
        if (isZip(source)) {
            // Reading every entry to its end makes Java check each entry's CRC: a backup with a damaged picture is
            // refused now, not half-way through the restore (TODO 2.42).
            boolean haveDb = false;
            long picturesBytes = 0;
            try (InputStream in = Files.newInputStream(source); ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.equals(DB_ENTRY)) {
                        copyCapped(zip, databaseCopy, limits.database());
                        haveDb = true;
                    } else if (name.equals(INFO_ENTRY)) {
                        byte[] raw = zip.readNBytes(MAX_INFO_BYTES + 1);
                        if (raw.length > MAX_INFO_BYTES) throw new IOException(I18n.t("backup.info.toolarge"));
                        String text = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : text.split("\\R")) {
                            if (line.startsWith("created: ")) created = line.substring(9).replace('T', ' ');
                            if (line.startsWith("pictures: not included")) picturesIncluded = false;
                        }
                    } else {
                        // counted against the same caps as the restore, so a "bomb" is refused while only being looked at
                        long size = copyCappedToNowhere(zip, limits.picture());
                        picturesBytes += size;
                        if (picturesBytes > limits.picturesTotal()) throw new IOException(I18n.t("backup.pictures.toolarge"));
                        if (!entry.isDirectory() && name.startsWith(IMAGES_FOLDER + "/")) pictures++;
                    }
                }
            } catch (java.util.zip.ZipException e) {
                throw new IOException(I18n.t("backup.corrupt.zip", e.getMessage()), e);
            }
            if (!haveDb) {
                throw new IOException(I18n.t("backup.notlunavera", DB_ENTRY));
            }
        } else {
            if (Files.size(source) > limits.database()) throw new IOException(I18n.t("backup.entry.toolarge"));
            Files.copy(source, databaseCopy, StandardCopyOption.REPLACE_EXISTING);
        }
        verifyDatabase(databaseCopy);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + databaseCopy.toAbsolutePath());
             Statement st = c.createStatement()) {
            int orders = 0, products = 0, customers = 0;
            String newest = null;
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*), MAX(order_date) FROM orders")) {
                if (rs.next()) { orders = rs.getInt(1); newest = rs.getString(2); }
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                if (rs.next()) products = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM customers")) {
                if (rs.next()) customers = rs.getInt(1);
            } catch (SQLException noCustomers) {
                // an old backup without the table: not a reason to refuse it
            }
            if (newest != null) newest = newest.replace('T', ' ');
            if (newest != null && newest.length() > 16) newest = newest.substring(0, 16);
            return new Summary(created != null && created.length() > 16 ? created.substring(0, 16) : created,
                    orders, newest, products, customers, pictures, picturesIncluded);
        } catch (SQLException e) {
            throw new IOException(I18n.t("backup.unreadable", e.getMessage()), e);
        }
    }

    // ---- a restore that is waiting for the next start (TODO 2.41)

    /** The parked restore file, if any. */
    public static Optional<Path> pendingFile() {
        Path zip = DataPaths.dataDir().resolve(PENDING_ZIP);
        if (Files.exists(zip)) return Optional.of(zip);
        Path db = DataPaths.dataDir().resolve(PENDING_DB);
        return Files.exists(db) ? Optional.of(db) : Optional.empty();
    }

    /** When the pending restore was prepared; empty when nothing is pending. */
    public static Optional<LocalDateTime> pendingSince() {
        try {
            Optional<Path> pending = pendingFile();
            if (pending.isEmpty()) return Optional.empty();
            return Optional.of(LocalDateTime.ofInstant(Files.getLastModifiedTime(pending.get()).toInstant(),
                    java.time.ZoneId.systemDefault()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Throws the parked restore away; the live data was never touched. @return true when something was removed */
    public static boolean cancelPendingRestore() {
        try {
            boolean removed = Files.deleteIfExists(DataPaths.dataDir().resolve(PENDING_ZIP));
            removed |= Files.deleteIfExists(DataPaths.dataDir().resolve(PENDING_DB));
            return removed;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not cancel the pending restore", e);
            return false;
        }
    }

    /** True when a pending restore was prepared more than {@code age} ago (it is probably forgotten). */
    public static boolean pendingIsOlderThan(Duration age) {
        return pendingSince().map(t -> Duration.between(t, LocalDateTime.now()).compareTo(age) > 0).orElse(false);
    }

    /** A copy is only usable if SQLite opens it, finds it intact and it has the app's tables. */
    static void verifyDatabase(Path file) throws IOException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new IOException(I18n.t("backup.integrity"));
                }
            }
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                int version = rs.next() ? rs.getInt(1) : 0;
                if (version > com.coffeeshop.coffeeshopmanagement.config.SchemaMigrations.LATEST_VERSION) {
                    throw new IOException(I18n.t("backup.newer", version));
                }
            }
            // A backup nobody can sign in to would lock the shop out after the restart (TODO 2A.29).
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE role = " + SqlStatus.ROLE_ADMIN + " AND status = " + SqlStatus.ACTIVE)) {
                if (rs.next() && rs.getInt(1) < 1) {
                    throw new IOException(I18n.t("backup.noadmin"));
                }
            } catch (SQLException noUsersTable) {
                // a missing users table is reported by the check below
            }
            for (String table : List.of("users", "products", "orders")) {
                try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                    if (!rs.next()) {
                        throw new IOException(I18n.t("backup.notdb", table));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException(I18n.t("backup.unreadable", e.getMessage()), e);
        }
    }

    /**
     * Call at start-up BEFORE the database is opened. If a restore is pending: saves the current data as
     * {@code backups/before-restore-<time>.zip}, then replaces the database (and the pictures, for a .zip backup).
     * A failure is reported, the pending file is renamed so it is not retried on every start, and nothing is half-applied
     * to the database (it is replaced by one move at the end).
     */
    public static RestoreResult applyPendingRestore() {
        if (!DatabaseConfig.isSqlite()) {
            return new RestoreResult(RestoreOutcome.NOTHING_PENDING, ""); // MariaDB is restored with phpMyAdmin, never at start-up
        }
        Path dataDir = DataPaths.dataDir();
        Path pendingZip = dataDir.resolve(PENDING_ZIP);
        Path pendingDb = dataDir.resolve(PENDING_DB);
        boolean zip = Files.exists(pendingZip);
        Path pending = zip ? pendingZip : pendingDb;
        if (!Files.exists(pending)) {
            return new RestoreResult(RestoreOutcome.NOTHING_PENDING, "");
        }
        try {
            Path database = DataPaths.databaseFile();
            Path backups = DataPaths.backupsDir();
            Files.createDirectories(backups);

            // TODO 2.85: after a crash the newest sales can still be in lunavera.db-wal. Open the file once so SQLite replays that
            // log, and fold it into the main file, BEFORE the safety copy is taken and before the side files are deleted below.
            boolean logFolded = !Files.exists(database) || foldLeftoverLogInto(database);

            Path safety = null;
            if (Files.exists(database)) {
                safety = backups.resolve(BEFORE_RESTORE_PREFIX + STAMP.format(LocalDateTime.now()) + ".zip");
                writeZip(safety, database, DataPaths.imagesDir(), dataDir);
                pruneBeforeRestoreQuietly(backups); // keeps the newest 3, this one included (TODO 4.28)
            }

            Path incoming = dataDir.resolve("lunavera.db.restoring");
            Path incomingImages = dataDir.resolve("images.restoring");
            Path oldImages = dataDir.resolve("images.old");
            deleteTree(incomingImages);
            deleteTree(oldImages);
            Path imagesDir = DataPaths.imagesDir();
            boolean imagesSwapped = false;
            List<String> accountNotes = List.of();
            try {
                if (zip) {
                    if (!extractEntry(pending, DB_ENTRY, incoming)) throw new IOException(I18n.t("backup.missing", DB_ENTRY));
                } else {
                    Files.copy(pending, incoming, StandardCopyOption.REPLACE_EXISTING);
                }
                verifyDatabase(incoming);
                accountNotes = accountsBroughtBack(database, incoming); // before the swap, while both files are still there (TODO 2A.22)
                if (zip && !picturesIncluded(pending)) {
                    // An automatic backup between two full ones has no pictures: the ones in the shop now stay (TODO 4.28).
                    LOGGER.info("The backup has no pictures; the current pictures are kept.");
                } else if (zip) {
                    // Pictures and the transfer QR are first extracted next to the live ones, and only swapped in once
                    // everything is ready: a failure here leaves the old pictures untouched (TODO 2.42).
                    extractImages(pending, incomingImages);
                    if (Files.isDirectory(imagesDir)) {
                        Files.move(imagesDir, oldImages);
                    }
                    Files.move(incomingImages, imagesDir);
                    imagesSwapped = true;
                }
                for (String suffix : List.of("-journal", "-wal", "-shm")) {
                    Path side = dataDir.resolve(DataPaths.DATABASE_FILE_NAME + suffix);
                    if (!logFolded && suffix.equals("-wal") && Files.exists(side)) {
                        // The log could not be folded in: keep it beside the safety copies instead of deleting sales (TODO 2.85).
                        Files.move(side, backups.resolve("lunavera.db-wal.unfolded-" + STAMP.format(LocalDateTime.now())),
                                StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.deleteIfExists(side);
                    }
                }
                Files.move(incoming, database, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception failed) {
                // Put the old pictures back if they were already moved away, and clean up the temporary files.
                if (imagesSwapped || (Files.isDirectory(oldImages) && !Files.exists(imagesDir))) {
                    deleteTree(imagesDir);
                    if (Files.isDirectory(oldImages)) Files.move(oldImages, imagesDir);
                }
                deleteTree(incomingImages);
                Files.deleteIfExists(incoming);
                throw failed;
            }
            deleteTree(oldImages);
            if (zip) restoreTransferQr(pending, dataDir);
            Files.deleteIfExists(pending);
            pruneFailedRestores(dataDir, 3);
            String where = safety == null ? "" : " " + I18n.t("backup.safety", safety);
            LOGGER.info("Restored from the pending backup." + where);
               String accounts = accountNotes.isEmpty() ? ""
                    : I18n.t("backup.restored.accounts", String.join("\n- ", accountNotes));
               return new RestoreResult(RestoreOutcome.RESTORED, I18n.t("backup.restored", where) + accounts);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Restore failed", e);
            try {
                Files.move(pending, dataDir.resolve("restore-failed-" + STAMP.format(LocalDateTime.now()) + (zip ? ".zip" : ".db")),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // leave it; the message below still tells the person
            }
            pruneFailedRestores(dataDir, 3);
            return new RestoreResult(RestoreOutcome.FAILED, I18n.t("backup.restore.failed", e.getMessage()));
        }
    }

    /**
     * Folds the write-ahead log that a crash left next to {@code database} into the main file (TODO 2.85). The restore runs
     * before the database is opened, so nothing has recovered the log yet; opening the file once makes SQLite replay it, and
     * {@code wal_checkpoint(TRUNCATE)} moves its pages into {@code lunavera.db}. A copy of the bare file taken without this
     * misses every sale since the last checkpoint.
     *
     * @return true if there was nothing to fold or it was folded; false if the file could not be opened (the caller then keeps
     *         the log file instead of deleting it)
     */
    static boolean foldLeftoverLogInto(Path database) {
        Path log = database.resolveSibling(database.getFileName() + "-wal");
        try {
            if (!Files.exists(log) || Files.size(log) == 0) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not fold the database log into its file before the safety copy", e);
            return false;
        }
    }

    /**
     * What a restore would undo about accounts (TODO 2A.22): accounts that are in {@code restored} but were removed from
     * {@code current}, accounts that were locked in {@code current} and are active in {@code restored}, and accounts whose password
     * is an older one in {@code restored}. Only user names and the kind of change are returned, never a password hash. Empty when
     * there is no current database or either file cannot be read (the restore itself must not fail because of this look).
     */
    static List<String> accountsBroughtBack(Path current, Path restored) {
        List<String> notes = new ArrayList<>();
        if (current == null || restored == null || !Files.isRegularFile(current) || !Files.isRegularFile(restored)) return notes;
        try {
            java.util.Map<String, String[]> now = readAccounts(current);
            java.util.Map<String, String[]> then = readAccounts(restored);
            for (java.util.Map.Entry<String, String[]> e : then.entrySet()) {
                String name = e.getKey();
                String[] back = e.getValue();
                String[] had = now.get(name);
                if (had == null) {
                    notes.add(I18n.t("backup.account.back", name));
                } else if ("LOCKED".equals(had[1]) && !"LOCKED".equals(back[1])) {
                    notes.add(I18n.t("backup.account.unlocked", name));
                } else if (!had[0].equals(back[0])) {
                    notes.add(I18n.t("backup.account.oldpw", name));
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not compare the accounts before the restore", e);
        }
        return notes;
    }

    /** user name -> {password hash, status} */
    private static java.util.Map<String, String[]> readAccounts(Path file) throws SQLException {
        java.util.Map<String, String[]> accounts = new java.util.TreeMap<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT username, password_hash, status FROM users")) {
            while (rs.next()) {
                accounts.put(rs.getString(1), new String[] {String.valueOf(rs.getString(2)), String.valueOf(rs.getString(3))});
            }
        }
        return accounts;
    }

    /** Extracts the {@code images/} entries of a backup into {@code target} (created fresh); names that climb out are skipped. */
    private static void extractImages(Path zipFile, Path target) throws IOException {
        Files.createDirectories(target);
        Path root = target.toAbsolutePath().normalize();
        long totalBytes = 0;
        try (InputStream in = Files.newInputStream(zipFile); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(IMAGES_FOLDER + "/")) continue;
                Path file = root.resolve(name.substring(IMAGES_FOLDER.length() + 1)).normalize();
                if (!file.startsWith(root)) continue; // a name like ../../x must never leave the folder
                Files.createDirectories(file.getParent());
                totalBytes += copyCapped(zip, file, MAX_PICTURE_BYTES);
                if (totalBytes > MAX_PICTURES_TOTAL_BYTES) {
                    throw new IOException(I18n.t("backup.pictures.toolarge"));
                }
            }
        }
    }

    /** The bank-transfer QR picture is part of a backup too; it comes back with a restore (TODO 2.42). */
    private static void restoreTransferQr(Path zipFile, Path dataDir) {
        try (InputStream in = Files.newInputStream(zipFile); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            boolean cleared = false;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("qr-transfer.") || name.contains("/") || name.contains("\\")) continue;
                if (!cleared) {
                    try (DirectoryStream<Path> old = Files.newDirectoryStream(dataDir, "qr-transfer.*")) {
                        for (Path file : old) Files.deleteIfExists(file);
                    }
                    cleared = true;
                }
                copyCapped(zip, dataDir.resolve(name), MAX_PICTURE_BYTES);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not restore the transfer QR picture", e);
        }
    }

    /** Keeps only the newest {@code keep} {@code restore-failed-*} files in the data folder. */
    static void pruneFailedRestores(Path dataDir, int keep) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "restore-failed-*")) {
            List<Path> all = new ArrayList<>();
            stream.forEach(all::add);
            all.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
            for (int i = keep; i < all.size(); i++) Files.deleteIfExists(all.get(i));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not tidy old failed restores", e);
        }
    }

    private static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** Size limits for what a restore may write (TODO 2A.21): a crafted zip must not fill the disk. */
    static final long MAX_DATABASE_BYTES = 1L << 30;           // 1 GiB
    static final long MAX_PICTURE_BYTES = 50L << 20;           // 50 MiB per picture or QR
    static final long MAX_PICTURES_TOTAL_BYTES = 2L << 30;     // 2 GiB of pictures altogether

    /** Copies at most {@code maxBytes}; a longer stream stops, the partial file is removed and an IOException is thrown. */
    static long copyCapped(InputStream in, Path target, long maxBytes) throws IOException {
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (java.io.OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException(I18n.t("backup.entry.toolarge"));
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return total;
    }

    /** Reads and discards the rest of the entry (so its CRC is checked), refusing one longer than {@code maxBytes}. */
    private static long copyCappedToNowhere(InputStream in, long maxBytes) throws IOException {
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > maxBytes) throw new IOException(I18n.t("backup.entry.toolarge"));
        }
        return total;
    }

    private static boolean extractEntry(Path zipFile, String entryName, Path target) throws IOException {
        try (InputStream in = Files.newInputStream(zipFile); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    copyCapped(zip, target, MAX_DATABASE_BYTES);
                    return true;
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException(I18n.t("backup.notzip"), e);
        }
        return false;
    }
}
