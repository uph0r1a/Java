package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Puts the data of a MariaDB backup ({@code lunavera.sql}, a {@code mysqldump} text file) back into the shop's database from
 * inside the app (TODO 1.8). Until now only a SQLite shop could do this; a MariaDB shop followed the phpMyAdmin steps by hand.
 *
 * <p>The order of work, and what each step protects:
 * <ol>
 *   <li><b>One at a time.</b> The server lock {@link DatabaseConfig#MIGRATION_LOCK} is taken without waiting: it is the lock a till
 *       holds while it creates or upgrades the tables, so a restore never runs under a till that is starting, nor twice at once.
 *       A till that starts during a restore waits for it (up to a minute) and then carries on.</li>
 *   <li><b>Scratch check.</b> The file is run, statement by statement ({@link SqlDumpReader}: only what a dump of this app's tables
 *       holds), into a separate empty database named {@code <shop database>_restore_check}. Nothing of the shop is touched.
 *       There the backup is checked as data: a known schema version that is not newer than this program, only tables and columns this
 *       program knows, the three core tables, a usable administrator, no row that points at a missing row. The row counts are read
 *       there too. A damaged or foreign file stops here.</li>
 *   <li><b>Safety copy</b> ({@link Hooks#afterCheck()}): the caller dumps the current data and pictures before anything is changed.</li>
 *   <li><b>Swap, in one transaction.</b> The rows of every table of the app are deleted and the checked rows are copied across by
 *       the server ({@code INSERT ... SELECT} from the scratch database), the counts are compared, the caller swaps the picture folder
 *       ({@link Hooks#beforeCommit()}, the last step that can fail) and only then is the transaction committed. Any failure rolls
 *       everything back: other tills never see a half-restored shop, and until the commit they keep reading the old data.</li>
 *   <li>The scratch database is dropped at the end, whatever happened.</li>
 * </ol>
 *
 * <p>Why rows and not {@code DROP TABLE} / {@code RENAME TABLE}: those are DDL, which MariaDB commits by itself, so they cannot be
 * rolled back; and the database user documented in {@code docs/DATABASE.md} has no {@code DROP} right on the shop's tables. The
 * structure of the live tables is therefore never changed by a restore; a backup made by an older version is copied into the
 * current structure (the columns it has; the others take their defaults), and {@code schema_version} stays as it is.
 *
 * <p>What it needs from the server: the database user must be allowed to create and drop the scratch database
 * ({@code GRANT ALL ON <shop database>_restore_check.* TO ...}, docs/DATABASE.md section 6); without that the check says so and
 * stops before anything is changed. A scratch database that already exists and holds tables that are not this program's own
 * is never touched.
 */
public final class MariaDbRestore {

    private static final Logger LOGGER = Logger.getLogger(MariaDbRestore.class.getName());

    static final String SCRATCH_SUFFIX = "_restore_check";
    /** A table put into the scratch database so that a later run knows the database is the program's own and may be emptied. */
    static final String MARKER_TABLE = "lunavera_restore_marker";
    private static final String SCHEMA_VERSION_TABLE = "schema_version";
    /** Tables a backup must hold to be a backup of this shop at all. */
    private static final List<String> CORE_TABLES = List.of("users", "products", "orders");

    // MariaDB/MySQL error numbers the restore tells apart.
    private static final int ER_DBACCESS_DENIED = 1044;
    private static final int ER_ACCESS_DENIED = 1045;
    private static final int ER_TABLEACCESS_DENIED = 1142;
    private static final int ER_SPECIFIC_ACCESS_DENIED = 1227;
    private static final int ER_LOCK_WAIT_TIMEOUT = 1205;
    private static final int ER_LOCK_DEADLOCK = 1213;

    private MariaDbRestore() {
    }

    /** What the caller does around the swap. */
    public interface Hooks {
        /** After the scratch check, before anything of the shop is changed: the safety copy, the pictures put aside. A failure stops the restore. */
        void afterCheck() throws Exception;

        /** Inside the transaction, after the rows are in and counted, before the commit: the picture folder is swapped. A failure rolls the rows back. */
        void beforeCommit() throws Exception;

        /** The commit itself failed after {@link #beforeCommit()} had worked: put the pictures back. Must not throw. */
        void undoBeforeCommit();
    }

    /** What the scratch check found; the numbers are what the confirmation shows. */
    public record Checked(int schemaVersion, long orders, String newestOrder, long products, long customers,
                          Map<String, Long> rows, Map<String, List<String>> copyColumns) {
    }

    /** What a finished restore did. */
    public record Result(int schemaVersion, Map<String, Long> rows) {
    }

    /** Where the connections come from; a test hands in its own. */
    interface Connections {
        /** A new connection to the shop's database. */
        Connection live() throws SQLException;

        /** A new connection to the scratch database {@code database}. */
        Connection scratch(String database) throws SQLException;

        /** The name of the shop's database. */
        String liveDatabase();
    }

    private static Connections real() {
        DatabaseSettings settings = DatabaseConfig.settings();
        return new Connections() {
            @Override
            public Connection live() throws SQLException {
                return DatabaseConfig.getConnection();
            }

            @Override
            public Connection scratch(String database) throws SQLException {
                return java.sql.DriverManager.getConnection(settings.mariaDbUrl(database), settings.user(), settings.password());
            }

            @Override
            public String liveDatabase() {
                return settings.database();
            }
        };
    }

    /** The name of the scratch database for a shop database. */
    static String scratchName(String shopDatabase) {
        return shopDatabase + SCRATCH_SUFFIX;
    }

    /** Checks {@code sqlFile} on the scratch database and drops it again; changes nothing of the shop. */
    public static Checked check(Path sqlFile) throws IOException {
        return check(real(), sqlFile);
    }

    /** The whole restore (see the class comment). Nothing of the shop has changed when this throws. */
    public static Result restore(Path sqlFile, Hooks hooks) throws IOException {
        return restore(real(), sqlFile, hooks);
    }

    static Checked check(Connections cx, Path sqlFile) throws IOException {
        String scratch = scratchName(cx.liveDatabase());
        try (Connection lock = cx.live()) {
            takeLock(lock);
            try {
                prepareScratch(lock, scratch); // refuses a database that is not ours, before anything can drop it
                try {
                    return checkUnderLock(cx, scratch, sqlFile);
                } finally {
                    dropScratchQuietly(lock, scratch); // ours: prepareScratch made it just now
                }
            } finally {
                ServerLock.release(lock, DatabaseConfig.MIGRATION_LOCK);
            }
        } catch (SQLException e) {
            throw problem(e, scratch);
        }
    }

    static Result restore(Connections cx, Path sqlFile, Hooks hooks) throws IOException {
        String scratch = scratchName(cx.liveDatabase());
        try (Connection lock = cx.live()) {
            takeLock(lock);
            try {
                prepareScratch(lock, scratch); // refuses a database that is not ours, before anything can drop it
                try {
                    Checked checked = checkUnderLock(cx, scratch, sqlFile);
                    try {
                        hooks.afterCheck();
                    } catch (IOException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IOException(I18n.t("backup.restore.maria.safety.failed", String.valueOf(e.getMessage())), e);
                    }
                    swap(cx, checked, scratch, hooks);
                    return new Result(checked.schemaVersion(), checked.rows());
                } finally {
                    dropScratchQuietly(lock, scratch); // ours: prepareScratch made it just now
                }
            } finally {
                ServerLock.release(lock, DatabaseConfig.MIGRATION_LOCK);
            }
        } catch (SQLException e) {
            throw problem(e, scratch);
        }
    }

    private static void takeLock(Connection lock) throws IOException, SQLException {
        if (!ServerLock.acquire(lock, DatabaseConfig.MIGRATION_LOCK, 0)) {
            throw new IOException(I18n.t("backup.restore.maria.busy"));
        }
    }

    // ------------------------------------------------------------------ the scratch check

    /** Runs the dump into the scratch database {@link #prepareScratch} made and applies the rules; the caller drops the scratch database. */
    private static Checked checkUnderLock(Connections cx, String scratch, Path sqlFile) throws IOException, SQLException {
        loadInto(cx, scratch, sqlFile);
        return inspect(cx, scratch);
    }

    /** Creates an empty scratch database, refusing to touch one that is not the program's own. */
    private static void prepareScratch(Connection admin, String scratch) throws IOException, SQLException {
        try (PreparedStatement exists = admin.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?")) {
            exists.setString(1, scratch);
            long tables;
            try (ResultSet rs = exists.executeQuery()) {
                tables = rs.next() ? rs.getLong(1) : 0;
            }
            if (tables > 0 && !hasMarker(admin, scratch)) {
                throw new IOException(I18n.t("backup.restore.maria.foreign", scratch));
            }
        }
        try (Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + scratch + "`");
            statement.execute("CREATE DATABASE `" + scratch + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                statement.execute("CREATE TABLE `" + scratch + "`.`" + MARKER_TABLE + "` (id TINYINT NOT NULL PRIMARY KEY)");
            } catch (SQLException e) {
                dropScratchQuietly(admin, scratch); // made a moment ago by this call, so it is ours to remove
                throw e;
            }
        } catch (SQLException e) {
            if (isAccessDenied(e)) {
                LOGGER.log(Level.WARNING, "The database user may not create the scratch database " + scratch, e);
                throw new IOException(I18n.t("backup.restore.maria.privileges", scratch), e);
            }
            throw e;
        }
    }

    private static boolean hasMarker(Connection admin, String scratch) throws SQLException {
        try (PreparedStatement marker = admin.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            marker.setString(1, scratch);
            marker.setString(2, MARKER_TABLE);
            try (ResultSet rs = marker.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }

    /**
     * The {@code sql_mode} of the scratch session: the server's own, without the strict modes, plus {@code NO_AUTO_VALUE_ON_ZERO}.
     * {@code mariadb-dump} writes the value of a stored generated column (such as {@code orders.open_table_key}) into the {@code INSERT};
     * a strict server refuses that ("The value specified for generated column ... has been ignored"), a lenient one ignores the value.
     * Only the scratch copy is loaded this way; the rows go into the live tables later, one column list at a time, without the generated
     * columns and under the live session's own (strict) mode.
     */
    static String scratchSqlMode(String serverMode) {
        List<String> modes = new ArrayList<>();
        if (serverMode != null) {
            for (String mode : serverMode.split(",")) {
                String name = mode.trim();
                if (!name.isEmpty() && !name.startsWith("STRICT_") && !name.equals("NO_AUTO_VALUE_ON_ZERO")) {
                    modes.add(name);
                }
            }
        }
        modes.add("NO_AUTO_VALUE_ON_ZERO");
        return String.join(",", modes);
    }

    /** Runs the structure and the rows of the dump in the scratch database, refusing a table this program does not have. */
    private static void loadInto(Connections cx, String scratch, Path sqlFile) throws IOException, SQLException {
        try (Connection connection = cx.scratch(scratch); Statement statement = connection.createStatement();
             Reader reader = Files.newBufferedReader(sqlFile, StandardCharsets.UTF_8)) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0"); // a dump creates a table before the tables it points at
            String serverMode = "";
            try (ResultSet mode = statement.executeQuery("SELECT @@sql_mode")) {
                if (mode.next()) {
                    serverMode = mode.getString(1);
                }
            }
            statement.execute("SET SESSION sql_mode = '" + scratchSqlMode(serverMode) + "'"); // an id of 0 stays 0; generated columns take the value the dump holds without an error
            SqlDumpReader.read(reader, dumped -> {
                switch (dumped.kind()) {
                    case CREATE_TABLE, INSERT -> {
                        if (!MariaDbSchema.DROP_ORDER.contains(dumped.table())) {
                            throw new IOException(I18n.t("backup.restore.maria.unknowntable", dumped.table()));
                        }
                        statement.execute(dumped.sql());
                    }
                    default -> {
                        // DROP TABLE, LOCK, UNLOCK, SET: not run; the scratch database is empty and the session is set above
                    }
                }
            });
        } catch (CharacterCodingException e) {
            throw new IOException(I18n.t("backup.restore.sql.cut"), e); // not UTF-8 text: a damaged file
        } catch (IOException | SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(I18n.t("backup.restore.sql.cut"), e);
        }
    }

    /** Reads the loaded scratch database and applies every rule a backup must pass. */
    private static Checked inspect(Connections cx, String scratch) throws IOException, SQLException {
        try (Connection s = cx.scratch(scratch); Connection live = cx.live()) {
            Map<String, List<String>> scratchColumns = columns(s, scratch, false);
            scratchColumns.remove(MARKER_TABLE);
            if (!scratchColumns.containsKey(SCHEMA_VERSION_TABLE)) {
                throw new IOException(I18n.t("backup.restore.maria.notlunavera"));
            }
            int version = schemaVersion(s);
            if (version <= 0) {
                throw new IOException(I18n.t("backup.restore.maria.notlunavera"));
            }
            if (version > MariaDbMigrations.LATEST_VERSION) {
                throw new IOException(I18n.t("backup.newer", version));
            }
            for (String core : CORE_TABLES) {
                if (!scratchColumns.containsKey(core)) {
                    throw new IOException(I18n.t("backup.notdb", core));
                }
            }
            Map<String, List<String>> liveColumns = columns(live, cx.liveDatabase(), false);
            Map<String, List<String>> liveCopyable = columns(live, cx.liveDatabase(), true);
            Map<String, List<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> table : scratchColumns.entrySet()) {
                String name = table.getKey();
                if (name.equals(SCHEMA_VERSION_TABLE)) {
                    continue; // the live version stays: the live structure is what the data goes into
                }
                List<String> inLive = liveColumns.get(name);
                if (inLive == null) {
                    throw new IOException(I18n.t("backup.restore.maria.unknowntable", name));
                }
                for (String column : table.getValue()) {
                    if (!inLive.contains(column)) {
                        throw new IOException(I18n.t("backup.restore.maria.unknowncolumn", name, column));
                    }
                }
                List<String> columns = new ArrayList<>();
                for (String column : liveCopyable.get(name)) {
                    if (table.getValue().contains(column)) {
                        columns.add(column);
                    }
                }
                copy.put(name, columns);
            }
            Map<String, Long> rows = new LinkedHashMap<>();
            for (String name : copy.keySet()) {
                rows.put(name, count(s, name));
            }
            requireAdmin(s);
            requireNoOrphans(s, scratch, scratchColumns);
            String newest = newestOrder(s);
            return new Checked(version, rows.getOrDefault("orders", 0L), newest, rows.getOrDefault("products", 0L),
                    rows.getOrDefault("customers", 0L), rows, copy);
        }
    }

    /** table name -> its columns in order; {@code copyableOnly} leaves out generated columns (they cannot be written). */
    private static Map<String, List<String>> columns(Connection connection, String schema, boolean copyableOnly) throws SQLException {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        String sql = "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?"
                + (copyableOnly ? " AND UPPER(EXTRA) NOT LIKE '%GENERATED%'" : "") + " ORDER BY TABLE_NAME, ORDINAL_POSITION";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    columns.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                }
            }
        }
        return columns;
    }

    private static int schemaVersion(Connection scratch) throws SQLException {
        try (Statement statement = scratch.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_version WHERE id = 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static String newestOrder(Connection scratch) throws SQLException {
        try (Statement statement = scratch.createStatement();
             ResultSet rs = statement.executeQuery("SELECT MAX(order_date) FROM orders")) {
            String newest = rs.next() ? rs.getString(1) : null;
            if (newest == null) {
                return null;
            }
            newest = newest.replace('T', ' ');
            return newest.length() > 16 ? newest.substring(0, 16) : newest;
        }
    }

    /** A backup nobody can sign in to would lock the shop out (the same rule as a SQLite restore, TODO 2A.29). */
    private static void requireAdmin(Connection scratch) throws IOException, SQLException {
        try (Statement statement = scratch.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users WHERE role = " + SqlStatus.ROLE_ADMIN
                     + " AND status = " + SqlStatus.ACTIVE)) {
            if (rs.next() && rs.getLong(1) < 1) {
                throw new IOException(I18n.t("backup.noadmin"));
            }
        }
    }

    /**
     * Every foreign key of the scratch tables is checked: a row that points at a row that is not there is refused, because the
     * swap runs with foreign-key checks off and would copy it in. (The app has only one-column keys; each column is checked alone.)
     */
    private static void requireNoOrphans(Connection scratch, String schema, Map<String, List<String>> tables) throws IOException, SQLException {
        List<String[]> keys = new ArrayList<>();
        try (PreparedStatement statement = scratch.prepareStatement(
                "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = ? AND REFERENCED_TABLE_NAME IS NOT NULL")) {
            statement.setString(1, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    keys.add(new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
                }
            }
        }
        for (String[] key : keys) {
            if (!tables.containsKey(key[0])) {
                continue;
            }
            boolean parentThere = tables.containsKey(key[2]);
            String sql = parentThere
                    ? "SELECT COUNT(*) FROM `" + key[0] + "` c LEFT JOIN `" + key[2] + "` p ON c.`" + key[1] + "` = p.`" + key[3]
                            + "` WHERE c.`" + key[1] + "` IS NOT NULL AND p.`" + key[3] + "` IS NULL"
                    : "SELECT COUNT(*) FROM `" + key[0] + "` WHERE `" + key[1] + "` IS NOT NULL";
            try (Statement statement = scratch.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
                long orphans = rs.next() ? rs.getLong(1) : 0;
                if (orphans > 0) {
                    throw new IOException(I18n.t("backup.restore.maria.orphans", key[0], orphans));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the swap

    private static void swap(Connections cx, Checked checked, String scratch, Hooks hooks) throws IOException, SQLException {
        boolean pictureStepStarted = false;
        try (Connection live = cx.live()) {
            List<String> liveTables = liveAppTables(live, cx.liveDatabase());
            live.setAutoCommit(false);
            try (Statement statement = live.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0"); // for this connection only; the checked rows are known to be consistent
                for (String table : liveTables) {
                    statement.executeUpdate("DELETE FROM `" + table + "`"); // DELETE, not TRUNCATE: it can be rolled back
                }
                for (Map.Entry<String, List<String>> table : checked.copyColumns().entrySet()) {
                    String list = backticked(table.getValue());
                    statement.executeUpdate("INSERT INTO `" + table.getKey() + "` (" + list + ") SELECT " + list + " FROM `" + scratch
                            + "`.`" + table.getKey() + "`");
                }
                for (Map.Entry<String, Long> table : checked.rows().entrySet()) {
                    long now = count(live, table.getKey());
                    if (now != table.getValue()) {
                        throw new IOException(I18n.t("backup.restore.maria.counts", table.getKey(), now, table.getValue()));
                    }
                }
                pictureStepStarted = true;
                hooks.beforeCommit(); // the pictures: the last step that can fail before the commit
                live.commit();
            } catch (Exception failed) {
                try {
                    live.rollback();
                } catch (SQLException rollbackFailure) {
                    failed.addSuppressed(rollbackFailure);
                }
                if (pictureStepStarted) {
                    hooks.undoBeforeCommit(); // idempotent: it also covers a picture step that stopped half way
                }
                if (failed instanceof IOException io) {
                    throw io;
                }
                if (failed instanceof SQLException sql) {
                    throw sql;
                }
                throw new IOException(I18n.t("backup.restore.failed", String.valueOf(failed.getMessage())), failed);
            } finally {
                try {
                    live.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // the connection is closed next
                }
            }
        }
    }

    /** The tables of the app that exist in the shop's database now, except the version table, children first. */
    private static List<String> liveAppTables(Connection live, String database) throws SQLException {
        List<String> present = new ArrayList<>();
        try (PreparedStatement statement = live.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'")) {
            statement.setString(1, database);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    present.add(rs.getString(1));
                }
            }
        }
        List<String> tables = new ArrayList<>();
        for (String table : MariaDbSchema.DROP_ORDER) { // only the app's own tables, in an order that suits a delete
            if (!table.equals(SCHEMA_VERSION_TABLE) && present.contains(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    private static String backticked(List<String> columns) {
        StringBuilder list = new StringBuilder();
        for (String column : columns) {
            if (list.length() > 0) {
                list.append(", ");
            }
            list.append('`').append(column).append('`');
        }
        return list.toString();
    }

    private static void dropScratchQuietly(Connection admin, String scratch) {
        try (Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + scratch + "`");
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "The scratch database " + scratch + " could not be dropped; the next restore does it", e);
        }
    }

    // ------------------------------------------------------------------ errors

    private static boolean isAccessDenied(SQLException e) {
        int code = e.getErrorCode();
        return code == ER_DBACCESS_DENIED || code == ER_ACCESS_DENIED || code == ER_TABLEACCESS_DENIED || code == ER_SPECIFIC_ACCESS_DENIED;
    }

    /** A database error as a plain message for the screen (the driver's own text goes to the log). */
    static IOException problem(SQLException e, String scratch) {
        LOGGER.log(Level.WARNING, "The restore stopped on a database error", e);
        int code = e.getErrorCode();
        if (code == ER_LOCK_WAIT_TIMEOUT || code == ER_LOCK_DEADLOCK) {
            return new IOException(I18n.t("backup.restore.maria.locked"), e);
        }
        if (isAccessDenied(e)) {
            return new IOException(I18n.t("backup.restore.maria.privileges", scratch), e);
        }
        return new IOException(I18n.t("backup.restore.maria.failed"), e);
    }
}
