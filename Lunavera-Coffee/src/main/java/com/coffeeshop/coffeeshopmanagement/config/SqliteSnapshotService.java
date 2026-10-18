package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * An hourly safety copy of the shop's data as a plain <b>SQLite file</b>, kept next to a MariaDB/MySQL database.
 *
 * <p>While the shop runs on MariaDB, this copies every table into a new file
 * {@code data/snapshots/lunavera-yyyyMMdd-HHmmss.db} (one consistent view of the data), checks every table has the same
 * number of rows, and then deletes all but the newest {@value #KEEP} snapshots. A snapshot opens in any SQLite viewer
 * (DB Browser for SQLite) and can also be used by the app itself: copy it over {@code data/lunavera.db} and set
 * {@code db.type=sqlite}. It does not contain the product pictures (those are plain files in {@code data/images}).
 *
 * <p>It runs only when the shop's database is not SQLite (a SQLite shop already has the file itself and the backup
 * zips). The first snapshot is made shortly after start-up when the newest one is older than an hour, then every hour while
 * the app is running. A failure is logged and tried again at the next hour; it never stops the app. The file is written
 * under a temporary name and renamed when complete, so a half-written snapshot is never mistaken for a good one.
 */
public final class SqliteSnapshotService {

    /** How many snapshots are kept; older ones are deleted. */
    public static final int KEEP = 10;
    /** Minutes between snapshots. */
    public static final long INTERVAL_MINUTES = 60;
    private static final long FIRST_RUN_DELAY_SECONDS = 30;

    private static final Logger LOGGER = Logger.getLogger(SqliteSnapshotService.class.getName());
    private static final String PREFIX = "lunavera-";
    private static final String SUFFIX = ".db";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern NAME = Pattern.compile("lunavera-(\\d{8}-\\d{6})\\.db");

    private static ScheduledExecutorService scheduler;

    private SqliteSnapshotService() {
    }

    /** Opens the connection to read from (the shop's database); a parameter so a test can read from any database. */
    @FunctionalInterface
    interface ConnectionSource {
        Connection open() throws SQLException;
    }

    /** Starts the hourly job. Does nothing when the shop's database is SQLite itself, or when it is already running. */
    public static synchronized void start() {
        if (DatabaseConfig.isSqlite() || scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sqlite-snapshot");
            thread.setDaemon(true);
            return thread;
        });
        Path dir = DataPaths.snapshotsDir();
        long firstDelay = initialDelaySeconds(newestSnapshotTime(dir), LocalDateTime.now());
        scheduler.scheduleWithFixedDelay(SqliteSnapshotService::runQuietly, firstDelay, INTERVAL_MINUTES * 60, TimeUnit.SECONDS);
        LOGGER.info("SQLite snapshots every " + INTERVAL_MINUTES + " minutes into " + dir + " (keeping " + KEEP + "); the first in "
                + firstDelay + " seconds.");
    }

    private static void runQuietly() {
        try {
            Path made = createSnapshot(DatabaseConfig::getConnection, DataPaths.snapshotsDir(), LocalDateTime.now(), KEEP);
            LOGGER.info("SQLite snapshot written: " + made);
        } catch (Throwable e) { // never let one failure end the hourly job
            LOGGER.log(Level.WARNING, "The SQLite snapshot failed; it will be tried again in " + INTERVAL_MINUTES + " minutes", e);
        }
    }

    // ---------------------------------------------------------------- the copy

    /**
     * Copies the whole database read through {@code source} into a new snapshot file in {@code dir} and prunes old ones.
     *
     * @return the finished snapshot file
     */
    static Path createSnapshot(ConnectionSource source, Path dir, LocalDateTime now, int keep) throws IOException, SQLException {
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName(now));
        Path partial = dir.resolve(fileName(now) + ".partial");
        Files.deleteIfExists(partial);
        try {
            try (Connection from = source.open(); Connection snapshot = DriverManager.getConnection("jdbc:sqlite:" + partial.toAbsolutePath())) {
                copyAll(from, snapshot);
            }
            try {
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SQLException | RuntimeException e) {
            Files.deleteIfExists(partial); // never leave a half-written file behind
            throw e;
        }
        prune(dir, keep);
        return target;
    }

    private static void copyAll(Connection from, Connection snapshot) throws SQLException {
        SqlDialect sourceDialect = SqlDialect.of(from);
        from.setAutoCommit(false);
        if (sourceDialect == SqlDialect.MARIADB) {
            try (Statement start = from.createStatement()) {
                start.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT"); // every table below is read as of one moment
            }
        }
        try {
            try (Statement create = snapshot.createStatement()) {
                for (String ddl : SqliteSchema.TABLES) {
                    create.execute(ddl);
                }
                create.execute(SqlDialect.SQLITE.createFlagsTable());
            }
            for (String[] column : SqliteSchema.LATER_COLUMNS) {
                DatabaseConfig.addColumnIfMissing(snapshot, column[0], column[1], column[2]);
            }
            snapshot.setAutoCommit(false);
            for (SqliteToMariaDbImporter.TableSpec table : SqliteToMariaDbImporter.TABLES) {
                int copied = copyTable(from, snapshot, table);
                int found = count(snapshot, table.name());
                if (found != copied) {
                    throw new SQLException("Bảng " + table.name() + ": đã chép " + copied + " dòng nhưng bản chụp có " + found + ".");
                }
            }
            snapshot.commit();
            snapshot.setAutoCommit(true);
        } finally {
            from.rollback(); // nothing was written to the shop's database; this just ends the read view
        }
        // The version and the indexes, so the file is also a complete SQLite database for the app itself.
        DatabaseConfig.setUserVersion(snapshot, DatabaseConfig.CURRENT_SCHEMA_VERSION);
        SchemaMigrations.upgrade(snapshot);
    }

    private static int copyTable(Connection from, Connection snapshot, SqliteToMariaDbImporter.TableSpec table) throws SQLException {
        String columns = String.join(", ", table.columns());
        String insertSql = "INSERT INTO " + table.name() + " (" + columns + ") VALUES ("
                + String.join(", ", Collections.nCopies(table.columns().size(), "?")) + ")";
        int copied = 0;
        try (Statement select = from.createStatement();
             ResultSet rs = select.executeQuery("SELECT " + columns + " FROM " + table.name());
             PreparedStatement insert = snapshot.prepareStatement(insertSql)) {
            while (rs.next()) {
                for (int i = 0; i < table.columns().size(); i++) {
                    String column = table.columns().get(i);
                    insert.setObject(i + 1, toSqlite(table, column, rs));
                }
                insert.addBatch();
                if (++copied % 500 == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
        }
        return copied;
    }

    /** One value for the SQLite file: date-times become ISO text (as the SQLite app stores them), the rest is passed through. */
    private static Object toSqlite(SqliteToMariaDbImporter.TableSpec table, String column, ResultSet rs) throws SQLException {
        if (table.dateTimeColumns().contains(column)) {
            String text = rs.getString(column);
            return text == null ? null : DbTime.format(DbTime.parse(text), SqlDialect.SQLITE);
        }
        if (table.dateColumns().contains(column)) {
            String text = rs.getString(column);
            return text == null ? null : (text.length() > 10 ? text.substring(0, 10) : text);
        }
        Object value = rs.getObject(column);
        return value instanceof Boolean flag ? (flag ? 1 : 0) : value; // MariaDB's TINYINT(1) comes back as a boolean
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ---------------------------------------------------------------- names, pruning, timing (no database needed)

    static String fileName(LocalDateTime time) {
        return PREFIX + time.format(STAMP) + SUFFIX;
    }

    /** The time written in a snapshot's file name, or empty if the name is not a snapshot name. */
    static Optional<LocalDateTime> parseStamp(String fileName) {
        var matcher = NAME.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(matcher.group(1), STAMP));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** The snapshots to delete so that only the newest {@code keep} remain; files that are not snapshots are never listed. */
    static List<Path> filesToDelete(List<Path> candidates, int keep) {
        List<Path> snapshots = new ArrayList<>();
        for (Path path : candidates) {
            if (parseStamp(path.getFileName().toString()).isPresent()) {
                snapshots.add(path);
            }
        }
        snapshots.sort(Comparator.comparing((Path p) -> parseStamp(p.getFileName().toString()).orElseThrow()).reversed());
        return snapshots.size() <= keep ? List.of() : new ArrayList<>(snapshots.subList(Math.max(keep, 0), snapshots.size()));
    }

    static void prune(Path dir, int keep) throws IOException {
        List<Path> all;
        try (Stream<Path> files = Files.list(dir)) {
            all = files.filter(Files::isRegularFile).toList();
        }
        for (Path old : filesToDelete(all, keep)) {
            try {
                Files.deleteIfExists(old);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not delete the old snapshot " + old, e);
            }
        }
    }

    static Optional<LocalDateTime> newestSnapshotTime(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> parseStamp(p.getFileName().toString())).flatMap(Optional::stream).max(Comparator.naturalOrder());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Seconds until the first snapshot: soon when the newest is missing or at least an interval old, else when it falls due. */
    static long initialDelaySeconds(Optional<LocalDateTime> newest, LocalDateTime now) {
        if (newest.isEmpty()) {
            return FIRST_RUN_DELAY_SECONDS;
        }
        long age = Duration.between(newest.get(), now).getSeconds();
        long interval = INTERVAL_MINUTES * 60;
        return age >= interval || age < 0 ? FIRST_RUN_DELAY_SECONDS : Math.max(FIRST_RUN_DELAY_SECONDS, interval - age);
    }
}
