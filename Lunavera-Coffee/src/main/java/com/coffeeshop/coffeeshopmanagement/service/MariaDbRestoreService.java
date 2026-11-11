package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.MariaDbRestore;
import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Restores a backup from inside the app when the shop runs on MariaDB/MySQL (TODO 1.8); a SQLite shop keeps the restore at the
 * next start ({@link BackupService#scheduleRestore}). Two calls, as on SQLite: {@link #inspect} looks at the file and says what it
 * holds, {@link #restoreNow} replaces the data. The database work (lock, scratch database, one transaction) is in
 * {@link MariaDbRestore}; this class owns what lies around it:
 * <ul>
 *   <li>only an administrator, only on MariaDB, only a plain {@code .zip} that holds {@code lunavera.sql};</li>
 *   <li>the zip is read to its end first (every entry's checksum, the same size caps as a SQLite restore) and the dump is
 *       copied out to a temporary file;</li>
 *   <li>the safety copy {@code before-restore-<time>.zip} (dump and pictures) is made after the check and before anything changes;
 *       if it cannot be made nothing is restored;</li>
 *   <li>the pictures are extracted beside the live folder and swapped in as the last step before the commit
 *       ({@code images.old} is kept until then, so a failed commit puts them back); a backup between two full ones has no
 *       pictures and the ones in the shop stay, as on SQLite; the transfer QR picture comes back after the commit;</li>
 *   <li>the security log gets a line at the start, at the end and on a failure, and the audit log (in the restored data) gets one
 *       {@link AuditAction#BACKUP_RESTORED} line.</li>
 * </ul>
 */
public final class MariaDbRestoreService {

    private static final Logger LOGGER = Logger.getLogger(MariaDbRestoreService.class.getName());

    private MariaDbRestoreService() {
    }

    /** What the zip says about itself (the part the database does not tell). */
    record ZipFacts(String created, int pictures, boolean picturesIncluded) {
    }

    /** The checks every call starts with. */
    private static void requireAllowed(Path source) throws IOException {
        Permissions.requireAdmin("restore a backup");
        if (DatabaseConfig.isSqlite()) {
            throw new IOException(I18n.t("backup.restore.maria.notmaria"));
        }
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException(I18n.t("backup.notfound"));
        }
        if (!BackupService.isZip(source)) {
            throw new IOException(I18n.t("backup.notlunavera", BackupService.databaseEntryName(false)));
        }
    }

    /**
     * Looks at the backup and checks it on a scratch database; the shop's data and pictures are not touched.
     *
     * @throws IOException with a plain-language message when the file cannot be restored
     */
    public static BackupService.Summary inspect(Path source) throws IOException {
        requireAllowed(source);
        Path sql = DataPaths.createTempFile("lunavera-restore-sql", ".sql");
        try {
            ZipFacts facts = unpack(source, sql);
            MariaDbRestore.Checked checked = MariaDbRestore.check(sql);
            return summaryOf(facts, checked);
        } finally {
            Files.deleteIfExists(sql);
        }
    }

    /**
     * Replaces the shop's data with the backup now, after the checks and the safety copy described in the class comment.
     *
     * @return the text to show: that it is done and where the safety copy is
     * @throws IOException with a plain-language message; the shop's data is as it was when this throws
     */
    public static String restoreNow(Path source) throws IOException {
        requireAllowed(source);
        String actor = SecurityLog.actor();
        String file = source.getFileName().toString();
        SecurityLog.event(actor + " started restoring the MariaDB shop from the backup " + file);
        Path sql = DataPaths.createTempFile("lunavera-restore-sql", ".sql");
        Path dataDir = DataPaths.dataDir();
        Path imagesDir = DataPaths.imagesDir();
        Path incomingImages = dataDir.resolve("images.restoring");
        Path oldImages = dataDir.resolve("images.old");
        try {
            ZipFacts facts = unpack(source, sql);
            PicturesHook hooks = new PicturesHook(source, facts.picturesIncluded(), imagesDir, incomingImages, oldImages);
            MariaDbRestore.Result result = MariaDbRestore.restore(sql, hooks);
            // The commit is done: from here nothing may undo the restore, so what follows only tidies and reports.
            tidyAfterCommit(source, dataDir, oldImages);
            String where = hooks.safety == null ? "" : " " + I18n.t("backup.safety", hooks.safety);
            auditRestore(file, hooks.safety, result);
            SecurityLog.event(actor + " restored the MariaDB shop from the backup " + file + " (schema version "
                    + result.schemaVersion() + ", " + result.rows().getOrDefault("orders", 0L) + " orders)");
            LOGGER.info("Restored the MariaDB shop from " + file + "." + where);
            return I18n.t("backup.restored.maria", where);
        } catch (IOException | RuntimeException e) {
            SecurityLog.warn(actor + " tried to restore the MariaDB shop from the backup " + file + " and it was refused or failed;"
                    + " the data was not changed");
            LOGGER.log(Level.WARNING, "The MariaDB restore did not happen", e);
            throw e;
        } finally {
            Files.deleteIfExists(sql);
            deleteQuietly(incomingImages);
        }
    }

    private static BackupService.Summary summaryOf(ZipFacts facts, MariaDbRestore.Checked checked) {
        return new BackupService.Summary(facts.created(), (int) Math.min(Integer.MAX_VALUE, checked.orders()), checked.newestOrder(),
                (int) Math.min(Integer.MAX_VALUE, checked.products()), (int) Math.min(Integer.MAX_VALUE, checked.customers()),
                facts.pictures(), facts.picturesIncluded());
    }

    // ------------------------------------------------------------------ the zip

    /**
     * Reads the whole zip (so every entry's checksum is checked and a bomb is refused before anything starts), copies
     * {@code lunavera.sql} to {@code sqlTarget} and reads what {@code backup-info.txt} says. A zip with no dump is not a MariaDB backup.
     */
    static ZipFacts unpack(Path source, Path sqlTarget) throws IOException {
        String created = null;
        int pictures = 0;
        boolean picturesIncluded = true;
        boolean haveSql = false;
        long picturesBytes = 0;
        String sqlName = BackupService.databaseEntryName(false);
        try (InputStream in = Files.newInputStream(source); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals(sqlName)) {
                    BackupService.copyCapped(zip, sqlTarget, BackupService.MAX_DATABASE_BYTES);
                    haveSql = true;
                } else if (name.equals(BackupService.INFO_ENTRY)) {
                    byte[] raw = zip.readNBytes(BackupService.MAX_INFO_BYTES + 1);
                    if (raw.length > BackupService.MAX_INFO_BYTES) {
                        throw new IOException(I18n.t("backup.info.toolarge"));
                    }
                    for (String line : new String(raw, StandardCharsets.UTF_8).split("\\R")) {
                        if (line.startsWith("created: ")) created = line.substring(9).replace('T', ' ');
                        if (line.startsWith("pictures: not included")) picturesIncluded = false;
                    }
                } else {
                    picturesBytes += BackupService.copyCappedToNowhere(zip, BackupService.MAX_PICTURE_BYTES);
                    if (picturesBytes > BackupService.MAX_PICTURES_TOTAL_BYTES) {
                        throw new IOException(I18n.t("backup.pictures.toolarge"));
                    }
                    if (!entry.isDirectory() && name.startsWith(BackupService.IMAGES_FOLDER + "/")) pictures++;
                }
            }
        } catch (java.util.zip.ZipException e) {
            Files.deleteIfExists(sqlTarget);
            throw new IOException(I18n.t("backup.corrupt.zip", e.getMessage()), e);
        }
        if (!haveSql) {
            throw new IOException(I18n.t("backup.notlunavera", sqlName));
        }
        return new ZipFacts(created != null && created.length() > 16 ? created.substring(0, 16) : created, pictures, picturesIncluded);
    }

    // ------------------------------------------------------------------ around the swap

    /** The safety copy and the pictures, called by {@link MariaDbRestore} at the right moments. */
    private static final class PicturesHook implements MariaDbRestore.Hooks {
        private final Path source;
        private final boolean withPictures;
        private final Path imagesDir;
        private final Path incomingImages;
        private final Path oldImages;
        Path safety;
        private boolean swapped;

        PicturesHook(Path source, boolean withPictures, Path imagesDir, Path incomingImages, Path oldImages) {
            this.source = source;
            this.withPictures = withPictures;
            this.imagesDir = imagesDir;
            this.incomingImages = incomingImages;
            this.oldImages = oldImages;
        }

        @Override
        public void afterCheck() throws IOException {
            try {
                safety = BackupService.createBeforeRestoreBackup();
            } catch (SQLException | IOException e) {
                throw new IOException(I18n.t("backup.restore.maria.safety.failed", String.valueOf(e.getMessage())), e);
            }
            // Only now, with the safety copy safe, the pictures of the backup are put beside the live ones.
            BackupService.deleteTree(incomingImages);
            BackupService.deleteTree(oldImages);
            if (withPictures) {
                BackupService.extractImages(source, incomingImages);
            } else {
                LOGGER.info("The backup has no pictures; the current pictures are kept.");
            }
        }

        @Override
        public void beforeCommit() throws IOException {
            if (!withPictures) {
                return;
            }
            if (Files.isDirectory(imagesDir)) {
                Files.move(imagesDir, oldImages);
            }
            Files.move(incomingImages, imagesDir);
            swapped = true;
        }

        @Override
        public void undoBeforeCommit() {
            try {
                if (swapped || (Files.isDirectory(oldImages) && !Files.exists(imagesDir))) {
                    BackupService.deleteTree(imagesDir);
                    if (Files.isDirectory(oldImages)) {
                        Files.move(oldImages, imagesDir);
                    }
                }
                swapped = false;
            } catch (IOException | RuntimeException e) {
                LOGGER.log(Level.SEVERE, "The pictures could not be put back after a failed restore; the old ones are in " + oldImages, e);
            }
        }
    }

    private static void tidyAfterCommit(Path source, Path dataDir, Path oldImages) {
        deleteQuietly(oldImages);
        BackupService.restoreTransferQr(source, dataDir); // logs its own problem
    }

    private static void deleteQuietly(Path folder) {
        try {
            BackupService.deleteTree(folder);
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not remove " + folder, e);
        }
    }

    /** The line in the audit log (in the restored data, so it is the first thing the restored log shows); never fails the restore. */
    private static void auditRestore(String file, Path safety, MariaDbRestore.Result result) {
        AuditDAO.recordQuietly(AuditAction.BACKUP_RESTORED, AuditTarget.BACKUP, null, file, null, "MARIADB",
                safety == null ? null : safety.getFileName().toString());
    }
}
