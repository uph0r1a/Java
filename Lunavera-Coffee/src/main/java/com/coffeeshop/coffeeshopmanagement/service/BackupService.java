package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
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
    public static final Duration AUTOMATIC_EVERY = Duration.ofHours(24);
    static final String AUTO_PREFIX = "auto-";
    static final String BEFORE_RESTORE_PREFIX = "before-restore-";
    static final String PENDING_ZIP = "restore-pending.zip";
    static final String PENDING_DB = "restore-pending.db";
    static final String DB_ENTRY = DataPaths.DATABASE_FILE_NAME;
    static final String IMAGES_FOLDER = "images";
    static final String INFO_ENTRY = "backup-info.txt";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** What to tell someone who tries the in-app restore while the shop runs on MariaDB/MySQL. */
    public static final String MARIADB_RESTORE_HELP =
            "Cửa hàng đang dùng MariaDB/MySQL, nên không khôi phục trong ứng dụng. Cách làm: mở tệp sao lưu .zip, "
                    + "giải nén lunavera.sql, vào phpMyAdmin, chọn cơ sở dữ liệu rồi bấm \"Nhập\" (Import) và chọn tệp đó; "
                    + "sau đó chép thư mục images trong tệp zip vào thư mục data/images. Xem docs/DATABASE.md.";

    public enum RestoreOutcome { NOTHING_PENDING, RESTORED, FAILED }

    public record RestoreResult(RestoreOutcome outcome, String message) { }

    private BackupService() {
    }

    // ------------------------------------------------------------------ making backups

    /** Adds ".zip" when the name has no extension, so a typed name always gives a usable file (TODO 2.23). */
    public static Path withZipExtension(Path target) {
        String name = target.getFileName().toString();
        return name.toLowerCase().endsWith(".zip") ? target : target.resolveSibling(name + ".zip");
    }

    /** Writes a full backup (database + pictures) to {@code target}; returns the file actually written. */
    public static Path createBackup(Path target) throws SQLException, IOException {
        Path zip = withZipExtension(target).toAbsolutePath().normalize();
        // The target always ends in .zip, so it can never be the live lunavera.db (the 2.23 problem of a backup
        // that deleted the database it was meant to copy).
        Path folder = zip.getParent();
        if (zip.startsWith(DataPaths.imagesDir().toAbsolutePath().normalize())) {
            throw new IOException("Không lưu bản sao lưu vào thư mục ảnh sản phẩm của chương trình. Hãy chọn thư mục khác.");
        }
        Files.createDirectories(folder);
        if (!Files.isWritable(folder)) {
            throw new IOException("Không ghi được vào thư mục: " + folder);
        }
        long needed = sizeOf(DataPaths.databaseFile()) + sizeOf(DataPaths.imagesDir());
        if (Files.getFileStore(folder).getUsableSpace() < needed + 1_000_000L) {
            throw new IOException("Không đủ dung lượng trống trong thư mục: " + folder);
        }
        Path databaseCopy = DataPaths.createTempFile("lunavera-backup", DatabaseConfig.backupExtension());
        Path partial = zip.resolveSibling(zip.getFileName() + ".part");
        try {
            DatabaseConfig.backupTo(databaseCopy);
            writeZip(partial, databaseCopy, databaseEntryName(), DataPaths.imagesDir(), DataPaths.dataDir());
            // Only now does the new file replace an older one of the same name.
            Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(databaseCopy);
            Files.deleteIfExists(partial);
        }
        return zip;
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
        writeZip(zipFile, databaseFile, DB_ENTRY, imagesDir, dataDir);
    }

    private static void writeZip(Path zipFile, Path databaseFile, String databaseEntry, Path imagesDir, Path dataDir) throws IOException {
        try (OutputStream out = Files.newOutputStream(zipFile);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(INFO_ENTRY));
            zip.write(("Lunavera Coffee backup\ncreated: " + LocalDateTime.now() + "\ndatabase: "
                    + (DB_ENTRY.equals(databaseEntry) ? "SQLite (" + DB_ENTRY + ")" : "MariaDB/MySQL dump (" + databaseEntry + ")")
                    + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(databaseEntry));
            Files.copy(databaseFile, zip);
            zip.closeEntry();
            if (Files.isDirectory(imagesDir)) {
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
                return Optional.empty();
            }
            Path target = dir.resolve(AUTO_PREFIX + STAMP.format(LocalDateTime.now()) + ".zip");
            Path made = createBackup(target);
            pruneAutomatic(dir, KEEP_AUTOMATIC);
            pruneBeforeRestoreQuietly(dir);
            LOGGER.info("Automatic backup written: " + made);
            return Optional.of(made);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Automatic backup failed", e);
            return Optional.empty();
        }
    }

    static boolean isDue(Path dir, Instant now) throws IOException {
        Optional<Instant> newest = newestAutomatic(dir);
        return newest.isEmpty() || Duration.between(newest.get(), now).compareTo(AUTOMATIC_EVERY) >= 0;
    }

    private static Optional<Instant> newestAutomatic(Path dir) throws IOException {
        Instant newest = null;
        for (Path file : automaticBackups(dir)) {
            Instant modified = Files.getLastModifiedTime(file).toInstant();
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
        return pruneNewest(zipsWithPrefix(dir, AUTO_PREFIX), keep);
    }

    /**
     * Deletes the oldest {@code before-restore-*.zip} safety copies beyond {@code keep} (TODO 4.28). Touches only those
     * files: automatic backups, hand-made zips and {@code before-money-migration.db} are left alone.
     * @return how many were deleted
     */
    static int pruneBeforeRestore(Path dir, int keep) throws IOException {
        return pruneNewest(zipsWithPrefix(dir, BEFORE_RESTORE_PREFIX), keep);
    }

    /** Same, but a problem is only logged: a restore or a backup must never fail because of tidying. */
    private static void pruneBeforeRestoreQuietly(Path dir) {
        try {
            pruneBeforeRestore(dir, KEEP_BEFORE_RESTORE);
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not tidy old before-restore copies", e);
        }
    }

    private static int pruneNewest(List<Path> all, int keep) throws IOException {
        all.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()); // names carry the time, newest first
        int deleted = 0;
        for (int i = keep; i < all.size(); i++) {
            if (Files.deleteIfExists(all.get(i))) deleted++;
        }
        return deleted;
    }

    /** Every backup the app knows about in its own backups folder, newest first (for the restore dialog's start folder / listing). */
    public static List<Path> listBackups() {
        List<Path> found = new ArrayList<>();
        Path dir = DataPaths.backupsDir();
        if (!Files.isDirectory(dir)) return found;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return Files.isRegularFile(p) && (n.endsWith(".zip") || n.endsWith(".db"));
            }).forEach(found::add);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not list backups", e);
        }
        found.sort(Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed());
        return found;
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
            throw new IOException(MARIADB_RESTORE_HELP);
        }
        Path check = DataPaths.createTempFile("lunavera-restore-check", ".db");
        try {
            Summary summary = inspectInto(source, check);
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

    private static boolean isZip(Path source) {
        return source.getFileName().toString().toLowerCase().endsWith(".zip");
    }

    /** What a backup holds, shown before the person confirms a restore (TODO 5.22). */
    public record Summary(String created, int orders, String newestOrder, int products, int customers, int pictures) {
        public String describe() {
            return "Tạo lúc: " + (created == null ? "(không rõ)" : created)
                    + "\nSố đơn hàng: " + orders + (newestOrder == null ? "" : " (đơn mới nhất: " + newestOrder + ")")
                    + "\nSản phẩm: " + products + ", khách hàng: " + customers + ", ảnh: " + pictures;
        }
    }

    /** Validates a backup (without scheduling anything) and describes it. */
    public static Summary inspect(Path source) throws IOException {
        Path check = DataPaths.createTempFile("lunavera-inspect", ".db");
        try {
            return inspectInto(source, check);
        } finally {
            Files.deleteIfExists(check);
        }
    }

    private static Summary inspectInto(Path source, Path databaseCopy) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Không tìm thấy tệp sao lưu.");
        }
        String created = null;
        int pictures = 0;
        if (isZip(source)) {
            // Reading every entry to its end makes Java check each entry's CRC: a backup with a damaged picture is
            // refused now, not half-way through the restore (TODO 2.42).
            boolean haveDb = false;
            try (InputStream in = Files.newInputStream(source); ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.equals(DB_ENTRY)) {
                        Files.copy(zip, databaseCopy, StandardCopyOption.REPLACE_EXISTING);
                        haveDb = true;
                    } else if (name.equals(INFO_ENTRY)) {
                        String text = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : text.split("\\R")) {
                            if (line.startsWith("created: ")) created = line.substring(9).replace('T', ' ');
                        }
                    } else {
                        zip.transferTo(OutputStream.nullOutputStream());
                        if (!entry.isDirectory() && name.startsWith(IMAGES_FOLDER + "/")) pictures++;
                    }
                }
            } catch (java.util.zip.ZipException e) {
                throw new IOException("Tệp sao lưu bị hỏng hoặc không phải tệp zip hợp lệ: " + e.getMessage(), e);
            }
            if (!haveDb) {
                throw new IOException("Tệp này không phải bản sao lưu của Lunavera (không có " + DB_ENTRY + " bên trong).");
            }
        } else {
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
                    orders, newest, products, customers, pictures);
        } catch (SQLException e) {
            throw new IOException("Không đọc được tệp sao lưu: " + e.getMessage(), e);
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
                    throw new IOException("Tệp sao lưu bị hỏng (kiểm tra toàn vẹn không đạt).");
                }
            }
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                int version = rs.next() ? rs.getInt(1) : 0;
                if (version > com.coffeeshop.coffeeshopmanagement.config.SchemaMigrations.LATEST_VERSION) {
                    throw new IOException("Bản sao lưu này được tạo bởi một phiên bản Lunavera mới hơn (cấu trúc dữ liệu v" + version
                            + ") nên phiên bản này không mở được. Hãy cập nhật ứng dụng trước.");
                }
            }
            for (String table : List.of("users", "products", "orders")) {
                try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                    if (!rs.next()) {
                        throw new IOException("Tệp này không phải cơ sở dữ liệu của Lunavera (thiếu bảng " + table + ").");
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("Không đọc được tệp sao lưu: " + e.getMessage(), e);
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
            try {
                if (zip) {
                    if (!extractEntry(pending, DB_ENTRY, incoming)) throw new IOException("Bản sao lưu không có " + DB_ENTRY);
                } else {
                    Files.copy(pending, incoming, StandardCopyOption.REPLACE_EXISTING);
                }
                verifyDatabase(incoming);
                if (zip) {
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
                    Files.deleteIfExists(dataDir.resolve(DataPaths.DATABASE_FILE_NAME + suffix));
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
            String where = safety == null ? "" : " Dữ liệu trước đó được lưu tại: " + safety;
            LOGGER.info("Restored from the pending backup." + where);
            return new RestoreResult(RestoreOutcome.RESTORED, "Đã khôi phục dữ liệu từ bản sao lưu." + where);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Restore failed", e);
            try {
                Files.move(pending, dataDir.resolve("restore-failed-" + STAMP.format(LocalDateTime.now()) + (zip ? ".zip" : ".db")),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // leave it; the message below still tells the person
            }
            pruneFailedRestores(dataDir, 3);
            return new RestoreResult(RestoreOutcome.FAILED, "Không khôi phục được: " + e.getMessage()
                    + "\nDữ liệu hiện tại được giữ nguyên.");
        }
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
                    throw new IOException("Ảnh trong bản sao lưu quá lớn.");
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
                    throw new IOException("Một tệp trong bản sao lưu lớn bất thường nên bị từ chối.");
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
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
            throw new IOException("Tệp không phải là tệp zip hợp lệ.", e);
        }
        return false;
    }
}
