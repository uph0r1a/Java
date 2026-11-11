package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.FileSizeText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The "database health" line of the admin dashboard (TODO 4.24): how big the data is, when the last backup was made, which
 * table layout (schema version) the database has and how much room the backups take, with a warning when the last backup is
 * older than {@value #WARN_AFTER_DAYS} days or there is none. It makes the backup habit visible.
 *
 * <p>TODO 6.75: a second line counts the rows of the tables that only ever grow ({@link #COUNTED_TABLES}), so the owner can see
 * how much the shop keeps; what is kept and for how long is in {@code docs/DATABASE.md}, section 11.
 */
public final class DatabaseHealth {

    private static final Logger LOGGER = Logger.getLogger(DatabaseHealth.class.getName());
    public static final int WARN_AFTER_DAYS = 7;
    /** A backup whose file time is further ahead of the clock than this comes from a wrong date and says nothing (TODO 3.65). */
    static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(10);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * The tables whose rows are counted on the dashboard (TODO 6.75), in the order shown: the ones nothing ever trims ({@code orders},
     * the audit and stock logs, clock-ins, shift closes, bookings) and the two the owner may delete from by hand ({@code expenses},
     * {@code customers}). The names are fixed here and never come from a user, so they go into the SQL as they are.
     * {@code DatabaseHealthRowsTest} fails when one of them is not a table of both schemas.
     */
    static final List<String> COUNTED_TABLES = List.of("orders", "audit_log", "stock_log", "time_clock", "shift_closes",
            "expenses", "reservations", "customers");

    /**
     * @param databaseBytes size of the database, or {@code null} when it could not be found out
     * @param lastBackup    time of the newest backup in the backups folder, or {@code null} when there is none
     * @param schemaVersion the table layout version, or 0 when it could not be read
     * @param backupsBytes  total size of the backups folder
     * @param lastManual    time the app noted for the last copy saved by hand, or {@code null}
     * @param rows          row count per table of {@link #COUNTED_TABLES} (a table that could not be counted is missing); never null
     */
    public record Health(Long databaseBytes, Instant lastBackup, int schemaVersion, long backupsBytes, Instant lastManual,
                         Map<String, Long> rows) {

        public Health {
            rows = rows == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(rows));
        }

        /** A reading without the row counts (the line then shows none). */
        public Health(Long databaseBytes, Instant lastBackup, int schemaVersion, long backupsBytes, Instant lastManual) {
            this(databaseBytes, lastBackup, schemaVersion, backupsBytes, lastManual, null);
        }

        /** A reading without the time of the last hand-made copy (the line then says "none yet" for it). */
        public Health(Long databaseBytes, Instant lastBackup, int schemaVersion, long backupsBytes) {
            this(databaseBytes, lastBackup, schemaVersion, backupsBytes, null, null);
        }
    }

    private DatabaseHealth() {
    }

    /** Reads everything; every part that cannot be read is left empty rather than failing the dashboard. Call off the UI thread. */
    public static Health read() {
        Path backups = DataPaths.backupsDir();
        Instant now = Instant.now();
        return new Health(databaseSize(), newestBackup(backups, now).orElse(null), schemaVersion(), folderBytes(backups),
                com.coffeeshop.coffeeshopmanagement.util.BackupReminder.lastManual(), rowCounts());
    }

    /** Counts the rows of {@link #COUNTED_TABLES}; empty when the database cannot be reached. */
    private static Map<String, Long> rowCounts() {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return rowCounts(connection);
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.INFO, "Could not count the rows of the tables", e);
            return Map.of();
        }
    }

    /** One {@code COUNT(*)} per table; a table that cannot be counted is left out and the others are still counted. */
    static Map<String, Long> rowCounts(Connection connection) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : COUNTED_TABLES) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                if (rs.next()) counts.put(table, rs.getLong(1));
            } catch (SQLException | RuntimeException e) {
                LOGGER.log(Level.INFO, "Could not count the rows of " + table, e);
            }
        }
        return counts;
    }

    private static Long databaseSize() {
        try {
            if (DatabaseConfig.isSqlite()) {
                Path file = DataPaths.databaseFile();
                return Files.isRegularFile(file) ? Files.size(file) : null;
            }
            try (Connection connection = DatabaseConfig.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT SUM(data_length + index_length) FROM information_schema.tables "
                         + "WHERE table_schema = DATABASE()")) {
                if (rs.next()) {
                    long bytes = rs.getLong(1);
                    return rs.wasNull() ? null : bytes;
                }
            }
        } catch (IOException | SQLException | RuntimeException e) {
            LOGGER.log(Level.INFO, "Could not find out the database size", e);
        }
        return null;
    }

    private static int schemaVersion() {
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (DatabaseConfig.isSqlite()) {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
            return DatabaseConfig.readSchemaVersion(connection);
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.INFO, "Could not read the schema version", e);
            return 0;
        }
    }

    /**
     * The newest backup file in {@code dir}: an automatic {@code auto-*.zip}, a manual one saved there, or a locked
     * {@code .lvbak}. The safety copies made before a restore are not backups of the shop's day-to-day state and are left out.
     * A file dated more than {@link #CLOCK_TOLERANCE} after {@code now} is ignored.
     */
    static Optional<Instant> newestBackup(Path dir, Instant now) {
        if (!Files.isDirectory(dir)) return Optional.empty();
        Instant newest = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                boolean backup = (name.endsWith(".zip") || name.endsWith(".lvbak")) && !name.startsWith("before-restore-")
                        && !name.endsWith(".part") && !name.startsWith("restore-pending");
                if (!backup || !Files.isRegularFile(file)) continue;
                Instant modified = Files.getLastModifiedTime(file).toInstant();
                if (modified.isAfter(now.plus(CLOCK_TOLERANCE))) continue;
                if (newest == null || modified.isAfter(newest)) newest = modified;
            }
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Could not list the backups folder", e);
        }
        return Optional.ofNullable(newest);
    }

    /** Total size of the files in {@code dir} (0 when it does not exist or cannot be read). */
    static long folderBytes(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        long total = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) total += Files.size(file);
            }
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Could not measure the backups folder", e);
        }
        return total;
    }

    /** True when there is no backup or the newest is more than {@value #WARN_AFTER_DAYS} days old. */
    public static boolean backupIsStale(Instant lastBackup, Instant now) {
        return lastBackup == null || Duration.between(lastBackup, now).compareTo(Duration.ofDays(WARN_AFTER_DAYS)) > 0;
    }

    /** The line shown on the dashboard. */
    public static String describe(Health health, Instant now, ZoneId zone) {
        String size = health.databaseBytes() == null ? I18n.t("admin.health.unknown") : FileSizeText.format(health.databaseBytes());
        String last = health.lastBackup() == null
                ? I18n.t("admin.health.none")
                : LocalDateTime.ofInstant(health.lastBackup(), zone).format(WHEN);
        String version = health.schemaVersion() > 0 ? "v" + health.schemaVersion() : I18n.t("admin.health.unknown");
        // A copy saved by hand somewhere else (a USB stick) is not in data/backups, so it is shown from the time the app noted
        // when it was saved (TODO 4.24, 5.18).
        String manual = health.lastManual() == null
                ? I18n.t("admin.health.none")
                : LocalDateTime.ofInstant(health.lastManual(), zone).format(WHEN);
        String line = I18n.t("admin.health.line", size, last, version, FileSizeText.format(health.backupsBytes()))
                + " · " + I18n.t("admin.health.manual", manual);
        String rows = rowsText(health.rows());
        return rows.isEmpty() ? line : line + "\n" + I18n.t("admin.health.rows", rows);
    }

    /** "Orders 1234 · Audit log 5678 ..." for the tables that were counted, in the order of {@link #COUNTED_TABLES}; empty when none. */
    static String rowsText(Map<String, Long> rows) {
        StringBuilder text = new StringBuilder();
        for (String table : COUNTED_TABLES) {
            Long count = rows.get(table);
            if (count == null) continue;
            if (text.length() > 0) text.append(" · ");
            text.append(rowLabel(table)).append(' ').append(count);
        }
        return text.toString();
    }

    /** Literal keys, so the key checks of {@code I18nTest} see every one of them. */
    private static String rowLabel(String table) {
        return switch (table) {
            case "orders" -> I18n.t("admin.health.rows.orders");
            case "audit_log" -> I18n.t("admin.health.rows.audit");
            case "stock_log" -> I18n.t("admin.health.rows.stock");
            case "time_clock" -> I18n.t("admin.health.rows.clock");
            case "shift_closes" -> I18n.t("admin.health.rows.shifts");
            case "expenses" -> I18n.t("admin.health.rows.expenses");
            case "reservations" -> I18n.t("admin.health.rows.bookings");
            case "customers" -> I18n.t("admin.health.rows.customers");
            default -> table;
        };
    }

    /** The yellow warning under the line, or {@code null} when the last backup is recent enough. */
    public static String warning(Health health, Instant now) {
        if (health.lastBackup() == null) return I18n.t("admin.health.warn.never");
        if (!backupIsStale(health.lastBackup(), now)) return null;
        long days = Duration.between(health.lastBackup(), now).toDays();
        return I18n.t("admin.health.warn.old", String.valueOf(days));
    }
}
