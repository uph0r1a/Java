package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.UiSettings;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A second place for the automatic backups: a USB stick, another PC on the network, a synced folder (TODO 4.35). The daily
 * backup lives in {@code data/backups}, on the same disk as the database, so a dead disk, a stolen PC or ransomware takes the
 * data and every backup together. With a second folder set, every automatic backup is also copied there.
 *
 * <p>The folder is a setting of THIS till (in {@code ui-settings.properties}), because a USB stick belongs to one PC.
 * A copy that cannot be made (stick pulled out, network share asleep) is not lost: the next hourly check copies the newest
 * backup again ({@link #catchUp}). The dashboard shows when the last copy outside the PC was made, red after
 * {@value #STALE_AFTER_DAYS} days, and the last error.
 *
 * <p>Nothing here may stop the app: every public method that runs in the background swallows and records its problems.
 */
public final class SecondBackupFolder {

    private static final Logger LOGGER = Logger.getLogger(SecondBackupFolder.class.getName());

    static final String KEY_FOLDER = "backup.second.folder";
    static final String KEY_LAST = "backup.second.last";
    static final String KEY_ERROR = "backup.second.error";

    /** The copy outside the PC is "old" (red) after this many days. */
    public static final int STALE_AFTER_DAYS = 3;
    /** Automatic copies kept in the second folder. */
    public static final int KEEP = 14;

    private SecondBackupFolder() {
    }

    // ------------------------------------------------------------------ checking a folder before it is accepted

    /**
     * The i18n key of what is wrong with {@code folder} as a second backup place, or null when it is fine: it must exist, be a
     * folder, not be the data folder or inside it (that is the same disk, which is the point of the setting), not be the
     * backups folder, and accept a file (a small probe file is written and removed).
     */
    public static String problem(Path folder, Path dataDir) {
        if (folder == null || !Files.isDirectory(folder)) {
            return "backup2.problem.missing";
        }
        if (isInside(folder, dataDir)) {
            return "backup2.problem.insidedata";
        }
        Path probe = null;
        try {
            probe = Files.createTempFile(folder, "lunavera-write-test-", ".tmp");
            return null;
        } catch (IOException | RuntimeException e) {
            return "backup2.problem.notwritable";
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignored) {
                    // a probe file that stays behind is harmless
                }
            }
        }
    }

    /** True when {@code folder} is {@code dataDir} or lies below it (symbolic links resolved when the folders exist). */
    static boolean isInside(Path folder, Path dataDir) {
        Path a = real(folder);
        Path b = real(dataDir);
        return a.startsWith(b);
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    // ------------------------------------------------------------------ copying

    /**
     * Copies {@code backup} into {@code target} (through a {@code .part} file that is renamed when complete, so a pulled stick
     * never leaves a half file that looks like a backup), checks the size, then removes the oldest automatic copies beyond
     * {@link #KEEP}. A copy already there with the same size is left alone.
     *
     * @return the copy
     */
    static Path copyInto(Path backup, Path target) throws IOException {
        Path destination = target.resolve(backup.getFileName().toString());
        long size = Files.size(backup);
        if (Files.isRegularFile(destination) && Files.size(destination) == size) {
            return destination;
        }
        Path part = target.resolve(backup.getFileName() + ".part");
        try {
            Files.copy(backup, part, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(part) != size) {
                throw new IOException("the copy is " + Files.size(part) + " bytes, the backup " + size);
            }
            Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
        prune(target, KEEP);
        return destination;
    }

    /** Deletes the oldest {@code auto-*} backups beyond {@code keep}; the stamp in the name sorts by time. Other files are never touched. */
    static int prune(Path folder, int keep) throws IOException {
        List<Path> autos = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, BackupService.AUTO_PREFIX + "*")) {
            for (Path file : stream) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(file) && (name.endsWith(".zip") || name.endsWith(".lvbak"))) {
                    autos.add(file);
                }
            }
        }
        autos.sort((x, y) -> y.getFileName().toString().compareTo(x.getFileName().toString())); // newest first
        int deleted = 0;
        for (int i = keep; i < autos.size(); i++) {
            Files.deleteIfExists(autos.get(i));
            deleted++;
        }
        return deleted;
    }

    /** The newest {@code auto-*.zip} of {@code backupsDir} (by name), if any. */
    static Optional<Path> newestAutomatic(Path backupsDir) {
        if (!Files.isDirectory(backupsDir)) {
            return Optional.empty();
        }
        Path newest = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir, BackupService.AUTO_PREFIX + "*.zip")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file) && (newest == null
                        || file.getFileName().toString().compareTo(newest.getFileName().toString()) > 0)) {
                    newest = file;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Could not list the backups folder", e);
        }
        return Optional.ofNullable(newest);
    }

    // ------------------------------------------------------------------ the setting of this till

    /** The folder chosen on this till, or null. */
    public static Path configured() {
        String saved = UiSettings.get(KEY_FOLDER, "");
        return saved.isBlank() ? null : Path.of(saved);
    }

    /** Saves {@code folder} as the second place (the caller has checked it with {@link #problem}); null turns the setting off. */
    public static void configure(Path folder) {
        UiSettings.put(KEY_FOLDER, folder == null ? "" : folder.toAbsolutePath().toString());
        UiSettings.put(KEY_ERROR, "");
        if (folder == null) {
            UiSettings.put(KEY_LAST, "");
        }
    }

    /**
     * Called after an automatic backup was made and at every hourly check: copies the newest automatic backup to the second
     * folder when it is not there yet. Records the time of a success, or the error. Never throws.
     *
     * @return true when a copy was made now
     */
    public static boolean catchUp() {
        Path target = configured();
        if (target == null) {
            return false;
        }
        try {
            Optional<Path> newest = newestAutomatic(DataPaths.backupsDir());
            if (newest.isEmpty()) {
                return false;
            }
            // The time shown is the age of the BACKUP that is outside the PC, not the time of this check: when no new backup
            // is made any more (a failing backup, the program not running), the line must go red, not say "today".
            Instant backupTime = Files.getLastModifiedTime(newest.get()).toInstant();
            Path destination = target.resolve(newest.get().getFileName().toString());
            if (Files.isRegularFile(destination) && Files.size(destination) == Files.size(newest.get())) {
                markSuccess(backupTime); // already there: the outside copy is as new as the newest backup
                return false;
            }
            String problem = problem(target, DataPaths.dataDir());
            if (problem != null) {
                throw new IOException(I18n.t(problem));
            }
            copyInto(newest.get(), target);
            markSuccess(backupTime);
            LOGGER.info("Backup copied to the second folder: " + target);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not copy the backup to the second folder", e);
            UiSettings.put(KEY_ERROR, String.valueOf(e.getMessage()));
            return false;
        }
    }

    /** Notes the time of the newest backup that IS in the second folder (its own file time, see {@link #catchUp}). */
    private static void markSuccess(Instant backupTime) {
        UiSettings.put(KEY_LAST, backupTime.toString());
        UiSettings.put(KEY_ERROR, "");
    }

    // ------------------------------------------------------------------ what the dashboard shows

    public enum State { NOT_SET, NEVER, OK, OLD, FAILED }

    /** @param lastCopy when the newest backup reached the second folder, or null; @param error the last problem, or "" */
    public record Status(Path folder, Instant lastCopy, String error) {
    }

    public static Status status() {
        Instant last = null;
        try {
            String text = UiSettings.get(KEY_LAST, "");
            last = text.isBlank() ? null : Instant.parse(text);
        } catch (RuntimeException e) {
            // an unreadable time is "never"
        }
        return new Status(configured(), last, UiSettings.get(KEY_ERROR, ""));
    }

    public static State stateOf(Status status, Instant now) {
        if (status.folder() == null) {
            return State.NOT_SET;
        }
        if (status.error() != null && !status.error().isBlank()) {
            return State.FAILED;
        }
        if (status.lastCopy() == null) {
            return State.NEVER;
        }
        // a time in the future (wrong PC clock) is not trusted as "fresh": it counts as never
        if (status.lastCopy().isAfter(now.plus(Duration.ofMinutes(10)))) {
            return State.NEVER;
        }
        return Duration.between(status.lastCopy(), now).compareTo(Duration.ofDays(STALE_AFTER_DAYS)) > 0 ? State.OLD : State.OK;
    }

    /** The line for the dashboard. */
    public static String describe(Status status, Instant now) {
        return switch (stateOf(status, now)) {
            case NOT_SET -> I18n.t("backup2.state.notset");
            case NEVER -> I18n.t("backup2.state.never", status.folder());
            case OK, OLD -> I18n.t("backup2.state.last", daysAgo(status.lastCopy(), now), status.folder());
            case FAILED -> I18n.t("backup2.state.failed", status.error(), status.folder());
        };
    }

    /** True when the line must be shown in the warning colour. */
    public static boolean needsAttention(Status status, Instant now) {
        State state = stateOf(status, now);
        return state == State.OLD || state == State.FAILED || state == State.NEVER;
    }

    private static String daysAgo(Instant last, Instant now) {
        long days = Math.max(0, Duration.between(last, now).toDays());
        return days == 0 ? I18n.t("backup2.today") : I18n.t("backup2.daysago", days);
    }
}
