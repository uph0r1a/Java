package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseHealth;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.AppVersion;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Saves one small zip for whoever looks after the program (TODO 5.42): the facts sheet, the newest log files and the last crash, with
 * nothing that identifies a person (see {@link SupportReport}). Only an administrator may make it. The zip is written to a temporary
 * file next to the target and moved into place, so a full disk never leaves half a zip under the chosen name.
 */
public final class SupportBundle {

    private static final Logger LOGGER = Logger.getLogger(SupportBundle.class.getName());
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    /** At most this many log files go in: the program keeps three, newest first. */
    static final int MAX_LOG_FILES = 3;
    /** A log file bigger than this is read from its end only (the program rotates at 1 MB). */
    static final long MAX_LOG_BYTES = 2_000_000L;

    private SupportBundle() {
    }

    /** The name offered in the save box, for example {@code lunavera-support-20261107-1530.zip}. */
    public static String suggestedName() {
        return "lunavera-support-" + AppClock.now().format(FILE_STAMP) + ".zip";
    }

    /** Makes the zip at {@code target} (an administrator only) and returns it. Call off the UI thread. */
    public static Path create(Path target) throws IOException {
        Permissions.requireAdmin("save the details for support");
        Path absolute = target.toAbsolutePath();
        Path folder = absolute.getParent();
        if (folder != null) Files.createDirectories(folder);
        Path temp = Files.createTempFile(folder == null ? Path.of(".") : folder, "support-", ".tmp");
        try {
            try (OutputStream file = Files.newOutputStream(temp); ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
                write(zip);
            }
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            SecurityLog.event(SecurityLog.actor() + " saved the details for support (facts, row counts and filtered log lines; no personal data)");
        } finally {
            Files.deleteIfExists(temp);
        }
        return absolute;
    }

    private static void write(ZipOutputStream zip) throws IOException {
        List<Path> logs = newestLogs();
        Map<String, String> logTexts = new LinkedHashMap<>();
        String crash = null;
        for (Path log : logs) {
            List<String> lines = SupportReport.linesOf(hidePaths(readText(log)));
            logTexts.put(log.getFileName().toString(), SupportReport.scrubbedTail(lines, SupportReport.LOG_TAIL_LINES));
            if (crash == null) crash = SupportReport.lastCrash(lines);
        }
        entry(zip, "support-info.txt", SupportReport.sheet(facts(logTexts.size())));
        entry(zip, "last-crash.txt", crash == null ? "No crash was found in the log files kept.\n" : crash);
        for (Map.Entry<String, String> log : logTexts.entrySet()) entry(zip, "logs/" + log.getKey(), log.getValue());
    }

    /** The data folder and the home folder are written as words before anything else is filtered (the facts sheet promises no path). */
    private static String hidePaths(String text) {
        String hidden = text;
        try {
            hidden = SupportReport.hidePath(hidden, DataPaths.dataDir().toAbsolutePath().toString(), "[data folder]");
        } catch (RuntimeException e) {
            LOGGER.log(Level.INFO, "The data folder could not be named for the support file", e);
        }
        return SupportReport.hidePath(hidden, System.getProperty("user.home"), "[home]");
    }

    private static SupportReport.Facts facts(int logFiles) {
        String kind = "unknown";
        int schema = 0;
        String problem = "";
        Map<String, Long> rows = new TreeMap<>();
        try {
            kind = DatabaseConfig.isSqlite() ? "SQLite (a file in the data folder)" : "MariaDB / MySQL (a server)";
            schema = DatabaseHealth.read().schemaVersion();
            try (Connection connection = DatabaseConfig.getConnection()) {
                rows.putAll(rowCountsOfEveryTable(connection));
            }
        } catch (SQLException | RuntimeException e) {
            // the class name only: a message may carry a host, a user or a path
            problem = "could not be read (" + e.getClass().getSimpleName() + ")";
            LOGGER.log(Level.INFO, "The database could not be read for the support file", e);
        }
        String system = System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")";
        return new SupportReport.Facts(AppVersion.logLine(), System.getProperty("java.version"), System.getProperty("java.vendor"), system,
                I18n.current(), kind, schema, rows, problem, AppClock.now().format(STAMP), logFiles);
    }

    /**
     * One {@code COUNT(*)} for every table the database has, by name and number only. A table that cannot be counted is left out and the
     * others are still counted. Names come from the database's own list and are checked ({@link SupportReport#safeTableName}) before they
     * go into the statement.
     */
    static Map<String, Long> rowCountsOfEveryTable(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && !name.toLowerCase(java.util.Locale.ROOT).startsWith("sqlite_") && SupportReport.safeTableName(name)) tables.add(name);
            }
        }
        tables.sort(Comparator.naturalOrder());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : tables) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                if (rs.next()) counts.put(table, rs.getLong(1));
            } catch (SQLException | RuntimeException e) {
                LOGGER.log(Level.INFO, "Could not count the rows of " + table, e);
            }
        }
        return counts;
    }

    /** The program's own log files ({@code app-*.log}, never the security log), newest first, at most {@link #MAX_LOG_FILES}. */
    static List<Path> newestLogs() {
        Path dir = DataPaths.logsDir();
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("app-") && n.endsWith(".log") && Files.isRegularFile(p);
                    })
                    .sorted(Comparator.comparingLong(SupportBundle::modified).reversed())
                    .limit(MAX_LOG_FILES).toList();
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.INFO, "Could not list the log files", e);
            return List.of();
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** A log file's text; a big file is read from its end only, and a damaged byte never stops the read. */
    private static String readText(Path file) throws IOException {
        long size = Files.size(file);
        byte[] bytes;
        if (size <= MAX_LOG_BYTES) {
            bytes = Files.readAllBytes(file);
        } else {
            try (java.nio.channels.SeekableByteChannel channel = Files.newByteChannel(file)) {
                channel.position(size - MAX_LOG_BYTES);
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) MAX_LOG_BYTES);
                while (buffer.hasRemaining() && channel.read(buffer) > 0) { /* fill */ }
                bytes = java.util.Arrays.copyOf(buffer.array(), buffer.position());
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
