package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.PasswordUtil;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the database connection and the one-time schema/seed setup, for either database the app can use:
 * <ul>
 *   <li><b>SQLite</b> (the default): one file, {@code data/lunavera.db} (see {@link DataPaths}); the first time a
 *       new version starts, data left in the old {@code ~/.lunavera-coffee} folder is copied across.</li>
 *   <li><b>MariaDB / MySQL</b>: chosen in {@code data/database.properties} (see {@link DatabaseSettings} and
 *       {@code docs/DATABASE.md}); this is what lets the shop use phpMyAdmin. SQLite data can be imported once.</li>
 * </ul>
 * The two differ only in a few SQL fragments, which come from {@link SqlDialect}; everything else in the DAOs is
 * plain SQL. Keep {@code data/} out of git: it holds customer phone numbers, password hashes and the database password.
 */
public final class DatabaseConfig {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    private static final String DB_FILE_NAME = DataPaths.DATABASE_FILE_NAME;
    /** Only used by tests now, and recognised at login so an old install that still has it is forced to change it (PasswordChangeDialog). */
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";
    /** v1: money columns are INTEGER dong instead of REAL (see MoneyMigration). */
    static final int CURRENT_SCHEMA_VERSION = 1;
    private static volatile boolean initialized = false;
    /** True once this run has switched the SQLite file to write-ahead logging (TODO 6.16); decides the per-connection pragma. */
    private static volatile boolean walActive = false;

    private DatabaseConfig() {
    }

    private static Path resolveDbFile() {
        DataPaths.migrateLegacyDataIfNeeded(); // before the first connection, so the copy is never open
        Path dir = DataPaths.dataDir();
        File dirFile = dir.toFile();
        if (!dirFile.exists() && !dirFile.mkdirs()) {
            // Refuse to start rather than quietly open an empty database somewhere else: the shop would see no
            // products and a fresh admin login and think its data was lost (TODO 2.26). The start-up dialog shows this.
            LOGGER.severe("Could not create the data directory " + dir);
            throw new IllegalStateException("Không tạo được thư mục dữ liệu: " + dir.toAbsolutePath());
        }
        return dir.resolve(DB_FILE_NAME);
    }

    /**
     * What this run resolved from the data folder: the settings, the SQLite file (null for MariaDB) and the JDBC URL. It is
     * computed on first use, not when the class loads (TODO 6.42), so that
     * <ul>
     *   <li>a failure (the folder cannot be created, a bad {@code database.properties}) reaches the caller as an exception
     *       and leaves nothing half-built: the next call tries again instead of the class being unusable for the rest of
     *       the run;</li>
     *   <li>the folder can be changed after first use by calling {@link #reset()} (a database move, and tests).</li>
     * </ul>
     */
    private record Resolved(DatabaseSettings settings, Path file, String url) {
    }

    private static volatile Resolved resolved;

    private static Resolved current() {
        Resolved r = resolved;
        if (r != null) {
            return r;
        }
        synchronized (DatabaseConfig.class) {
            if (resolved == null) {
                DatabaseSettings settings = DatabaseSettings.load();
                Path file = settings.isMariaDb() ? null : resolveDbFile();
                String url = settings.isMariaDb() ? settings.mariaDbUrl() : "jdbc:sqlite:" + file;
                resolved = new Resolved(settings, file, url); // only after everything above worked
            }
            return resolved;
        }
    }

    private static DatabaseSettings settings() {
        return current().settings();
    }

    private static Path dbFile() {
        return current().file();
    }

    private static String dbUrl() {
        return current().url();
    }

    /**
     * Forgets the resolved data folder and settings, so the next call reads {@code lunavera.dataDir} / {@code
     * LUNAVERA_DATA_DIR} and {@code database.properties} again, and {@link #initialize()} runs again against the new place.
     * Call it only when nobody is using a connection (before a database move, or between tests); it closes nothing itself.
     */
    public static synchronized void reset() {
        resolved = null;
        initialized = false;
        walActive = false;
    }

    /** The SQLite file this run is using, or {@code null} when the database is MariaDB/MySQL (check {@link #isSqlite()}). */
    public static Path databaseFile() {
        return dbFile();
    }

    /** Which database this run uses. */
    public static SqlDialect dialect() {
        return settings().dialect();
    }

    public static boolean isSqlite() {
        return settings().dialect() == SqlDialect.SQLITE;
    }

    /** The page the dashboard's "Mở phpMyAdmin" button opens (only offered when the database is MariaDB/MySQL). */
    public static String phpMyAdminUrl() {
        return settings().phpMyAdminUrl();
    }

    /** Where the app connects, for messages and logs (never includes the password). */
    public static String describeConnection() {
        return settings().isMariaDb() ? settings().describe() : "SQLite " + dbFile();
    }

    /**
     * Opens a new short-lived connection; callers should use try-with-resources. SQLite is file-based and handles many
     * short connections from a single-user desktop app well. For MariaDB/MySQL each call connects to the server;
     * a connection pool is a later step (see TODO 1.5).
     */
    public static Connection getConnection() throws SQLException {
        Resolved r = current();
        if (r.settings().isMariaDb()) {
            return DriverManager.getConnection(r.url(), r.settings().user(), r.settings().password());
        }
        Connection connection = DriverManager.getConnection(r.url());
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys = ON");
            // Without this, two connections writing at once (e.g. the 30s SessionGuard background
            // check landing mid-payment) get an immediate "database is locked" SQLException instead of
            // one of them just waiting briefly - SQLite's default busy behavior is to not wait at all.
            pragma.execute("PRAGMA busy_timeout = 5000");
            if (walActive) {
                // Only safe together with WAL (TODO 6.16): a commit then waits for the log, not for the disk, and a power cut
                // still cannot damage the file. In the old rollback-journal mode it would be a durability risk, so it stays off.
                pragma.execute("PRAGMA synchronous = NORMAL");
            }
        }
        registerSearchKeyFunction(connection);
        return connection;
    }

    /** The SQL function that {@link #searchKeySql} calls on SQLite (TODO 3.31). */
    static final String SEARCH_KEY_FUNCTION = "VN_KEY";

    /**
     * Gives this SQLite connection a {@code VN_KEY(text)} function that folds text the way the search boxes do
     * ({@link com.coffeeshop.coffeeshopmanagement.util.TextNormalizer#searchKey}: lower case, no accents, "đ" as "d").
     * SQLite's own {@code LIKE} and {@code lower()} only understand ASCII, so without it {@code nguyễn} never found
     * {@code Nguyễn}. A {@code NULL} becomes an empty text, which no search text matches.
     */
    private static void registerSearchKeyFunction(Connection connection) throws SQLException {
        org.sqlite.Function.create(connection, SEARCH_KEY_FUNCTION, new org.sqlite.Function() {
            @Override
            protected void xFunc() throws SQLException {
                String value = value_text(0);
                result(value == null ? "" : com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(value));
            }
        });
    }

    /**
     * The SQL for "this column, ready for a search match" (TODO 3.31). On SQLite it is {@code VN_KEY(column)}; on MariaDB the
     * column itself, because its {@code utf8mb4_unicode_ci} collation already ignores case and accents. Compare it with a
     * text made by {@code TextNormalizer.searchKey}.
     */
    public static String searchKeySql(String column) {
        return settings().isMariaDb() ? column : SEARCH_KEY_FUNCTION + "(" + column + ")";
    }

    /**
     * One short line that answers "is this my shop's data?" for the login screen (TODO 5.25): the data folder (SQLite) or the
     * server and database name (MariaDB), and the version of the table layout. Never the user name or the password.
     */
    public static String locationSummary() {
        return locationSummary(settings(), DataPaths.dataDir(), SchemaMigrations.LATEST_VERSION);
    }

    static String locationSummary(DatabaseSettings settings, java.nio.file.Path dataDir, int schemaVersion) {
        String where = settings.isMariaDb()
                ? "máy chủ " + settings.host() + ":" + settings.port() + "/" + settings.database()
                : dataDir.toAbsolutePath().normalize().toString();
        return "Dữ liệu: " + where + "  ·  cấu trúc v" + schemaVersion;
    }

    /** The file extension a backup of the current database should have. */
    public static String backupExtension() {
        return isSqlite() ? ".db" : ".sql";
    }

    /**
     * Writes a consistent copy of the whole database to {@code target}: SQLite's {@code VACUUM INTO} (safe while the
     * app is running, unlike copying the file by hand), or a {@code mysqldump} text file for MariaDB/MySQL.
     * Restoring is manual: SQLite - close the app and replace {@link #databaseFile()} with the backup; MariaDB - import
     * the .sql file (phpMyAdmin "Import" or the {@code mysql} command), see docs/DATABASE.md.
     *
     * <p>For SQLite it refuses to write onto the live database itself (that would erase it), and it writes to a {@code .part}
     * file first so that a failed copy never destroys an older file at the target.
     */
    public static void backupTo(java.nio.file.Path target) throws SQLException, java.io.IOException {
        if (settings().isMariaDb()) {
            MariaDbBackup.dumpTo(settings(), target);
            return;
        }
        if (isLiveDatabase(target)) {
            throw new java.io.IOException("Không thể ghi bản sao lưu đè lên chính cơ sở dữ liệu đang dùng ("
                    + dbFile() + "). Hãy chọn một tên tệp khác, tốt nhất ở thư mục ngoài thư mục dữ liệu.");
        }
        // Write beside the target and move it into place only when it is complete (TODO 6.25): if the copy fails half way
        // (disk full, the folder vanished) an older file at the target is still there, instead of having been deleted first.
        java.nio.file.Path wanted = target.toAbsolutePath().normalize();
        java.nio.file.Path partial = wanted.resolveSibling(wanted.getFileName() + ".part");
        try {
            java.nio.file.Files.deleteIfExists(partial); // VACUUM INTO refuses to overwrite
            try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("VACUUM INTO '" + partial.toString().replace("'", "''") + "'");
            }
            java.nio.file.Files.move(partial, wanted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            java.nio.file.Files.deleteIfExists(partial); // nothing left behind after a failure
        }
    }

    private static boolean isLiveDatabase(java.nio.file.Path target) throws java.io.IOException {
        java.nio.file.Path candidate = target.toAbsolutePath().normalize();
        if (candidate.equals(dbFile().toAbsolutePath().normalize())) {
            return true;
        }
        return java.nio.file.Files.exists(candidate) && java.nio.file.Files.exists(dbFile())
                && java.nio.file.Files.isSameFile(candidate, dbFile());
    }

    /**
     * Creates all tables if they do not already exist, seeds one default admin account the
     * very first time the application runs against an empty database, and seeds the starter
     * menu exactly once (see {@link MenuSeeder} - gated by its own persistent flag, not by
     * current table contents, so deleting starter items never brings them back). Safe to call
     * every time the application starts.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        // Before the first connection (SQLite creates the file on connect): a zero-byte file next to backups is a damaged
        // database, not a new shop (TODO 3.26). The start-up dialog shows this message.
        if (isSqlite()) {
            String damaged = DatabaseFileCheck.problem(dbFile());
            if (damaged != null) {
                LOGGER.severe(damaged);
                throw new IllegalStateException(damaged);
            }
        }
        try (Connection connection = getConnection()) {
            // Must check this *before* creating any table below: it's the only way to tell a
            // brand-new database (no migration needed - CREATE TABLE already uses the current
            // schema) apart from one that existed before schema versioning was added (PRAGMA
            // user_version defaults to 0 for both, so the version number alone can't tell them
            // apart - only "did any of our tables already exist" can).
            boolean preexisting = tableExists(connection, "users");
            SqlDialect dialect = SqlDialect.of(connection);

            try (Statement statement = connection.createStatement()) {
                for (String ddl : dialect == SqlDialect.SQLITE ? SqliteSchema.TABLES : MariaDbSchema.TABLES) {
                    statement.execute(ddl);
                }
            }
            // Columns added after the first release: a new MariaDB database already has them (nothing happens);
            // an older SQLite file gets them here. Checked first, so the table-rebuilding migration below keeps them.
            for (String[] column : SqliteSchema.LATER_COLUMNS) {
                addColumnIfMissing(connection, column[0], column[1], column[2]);
            }

            if (dialect == SqlDialect.SQLITE) {
                if (preexisting) {
                    MoneyMigration.runIfNeeded(connection, CURRENT_SCHEMA_VERSION);
                } else {
                    setUserVersion(connection, CURRENT_SCHEMA_VERSION);
                }
            } else if (readSchemaVersion(connection) == 0) {
                writeSchemaVersion(connection, SchemaMigrations.LATEST_VERSION); // a new MariaDB database: already the latest tables
            } else {
                MariaDbMigrations.upgrade(connection); // an existing one: apply any numbered steps it is missing
            }
            // Version 2 (indexes) and the unique rules; see SchemaMigrations (SQLite only: a new MariaDB database is
            // created with them).
            SchemaMigrations.upgrade(connection);

            // Before the default admin is seeded: an empty MariaDB database may be filled from the old SQLite file.
            SqliteToMariaDbImporter.importIfRequested(connection, settings());

            seedDefaultAdmin(connection);
            seedDiningTables(connection);
            MenuSeeder.seedIfNeeded(connection);
            mergeLegacyCategoryOnce(connection);
            if (dialect == SqlDialect.SQLITE) {
                enableWriteAheadLog(); // last, so the pre-migration backup above was taken in the old mode
            }
            initialized = true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database initialization failed", e);
            throw new IllegalStateException("Could not initialize the database", e);
        }
    }

    /** The flag that records the one-time merge below has run (TODO 6.7, 6.22). */
    static final String LEGACY_CATEGORY_MERGE_FLAG = "merge_ca_phe_v1";

    /**
     * One-time clean-up: an early version created a category named {@code Ca Phe} (no accents) next to {@code Cà phê}.
     * Moves its products to {@code Cà phê} and deletes it. It used to run at every start; now it runs once and records
     * that in {@code app_flags}, so a shop that adds its own category with that name later is never touched again.
     * Once every shop has started a version with this flag, the whole method can be deleted.
     */
    static void mergeLegacyCategoryOnce(Connection connection) throws SQLException {
        ensureFlagsTable(connection);
        try (var check = connection.prepareStatement("SELECT 1 FROM app_flags WHERE name = ?")) {
            check.setString(1, LEGACY_CATEGORY_MERGE_FLAG);
            try (var rs = check.executeQuery()) {
                if (rs.next()) {
                    return; // already done once
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            Integer caPheWithAccentId = null;
            Integer caPheNoAccentId = null;
            try (ResultSet rs = statement.executeQuery("SELECT id, name FROM categories WHERE name = 'Cà phê' OR name = 'Ca Phe'")) {
                while (rs.next()) {
                    if ("Cà phê".equals(rs.getString("name"))) {
                        caPheWithAccentId = rs.getInt("id");
                    } else if ("Ca Phe".equals(rs.getString("name"))) {
                        caPheNoAccentId = rs.getInt("id");
                    }
                }
            }
            if (caPheWithAccentId != null && caPheNoAccentId != null && !caPheWithAccentId.equals(caPheNoAccentId)) {
                statement.executeUpdate("UPDATE products SET category_id = " + caPheWithAccentId + " WHERE category_id = " + caPheNoAccentId);
                statement.executeUpdate("DELETE FROM categories WHERE id = " + caPheNoAccentId);
            }
        }
        // Recorded even when there was nothing to merge: the job is for databases that already had both names at this start.
        try (var insertFlag = connection.prepareStatement(
                SqlDialect.of(connection).insertIgnoreInto("app_flags") + " (name) VALUES (?)")) {
            insertFlag.setString(1, LEGACY_CATEGORY_MERGE_FLAG);
            insertFlag.executeUpdate();
        }
    }

    /** Adds a column to an existing table only when it is not there yet (checked, not guessed from an error). */
    static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        if (SqlDialect.of(connection) == SqlDialect.SQLITE) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    if (column.equalsIgnoreCase(rs.getString("name"))) {
                        return;
                    }
                }
            }
        } else {
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() "
                            + "AND table_name = ? AND column_name = ?")) {
                statement.setString(1, table);
                statement.setString(2, column);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                SqlDialect.of(connection).tableExistsQuery())) {
            statement.setString(1, tableName);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** PRAGMA statements don't support `?` placeholders - safe here since version is always
     *  this class's own int constant, never anything derived from user input. */
    static void setUserVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + version);
        }
    }

    /** System property that makes {@code initialize()} seed {@code admin} / {@link #DEFAULT_ADMIN_PASSWORD}; for tests only. */
    public static final String SEED_DEFAULT_ADMIN_PROPERTY = "lunavera.seedDefaultAdmin";

    /** True when there is no account at all, i.e. the owner must create the first administrator (TODO 2A.5). */
    public static boolean needsFirstRunSetup() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not check for existing accounts", e);
        }
    }

    /** Why the starter menu could not be created on this run, or null (TODO 3.38). */
    public static String starterMenuFailure() {
        return MenuSeeder.failure();
    }

    /**
     * Switches the SQLite file to write-ahead logging (TODO 6.16), so a reader (the dashboard, the 30-second session check)
     * is not blocked while the autosave writes. The mode is stored in the file, so after the first time this only confirms
     * it. If the switch is refused (a network drive, a locked file) the app carries on in the normal mode and says so in the
     * log: slower under contention, never wrong.
     *
     * <p>WAL adds {@code lunavera.db-wal} and {@code lunavera.db-shm} next to the database while the app runs; they go away
     * when the last connection closes. Backups are made with {@code VACUUM INTO} and a restore deletes these two files, so
     * neither is affected. Copying the database by hand must be done with the app closed (see docs/RELEASE.md).
     */
    private static void enableWriteAheadLog() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA journal_mode = WAL")) {
            walActive = rs.next() && "wal".equalsIgnoreCase(rs.getString(1));
            if (!walActive) {
                LOGGER.warning("The database stayed in its normal journal mode (WAL was not accepted).");
            }
        } catch (SQLException e) {
            walActive = false;
            LOGGER.log(Level.WARNING, "Could not switch the database to WAL; carrying on without it", e);
        }
    }

    /** Creates the small key/flag table used by the one-time seeders, in whichever database this is. */
    static void ensureFlagsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(SqlDialect.of(connection).createFlagsTable());
        }
    }

    /** MariaDB/MySQL only: the schema version (0 when none was written yet), replacing SQLite's PRAGMA user_version. */
    static int readSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_version WHERE id = 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    static void writeSchemaVersion(Connection connection, int version) throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_version (id, version) VALUES (1, ?) ON DUPLICATE KEY UPDATE version = VALUES(version)")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    private static void seedDefaultAdmin(Connection connection) throws SQLException {
        try (Statement check = connection.createStatement()) {
            var rs = check.executeQuery("SELECT COUNT(*) FROM users");
            rs.next();
            if (rs.getInt(1) > 0) {
                return; // already has data, never overwrite
            }
        }
        // No built-in administrator any more (TODO 2A.5): the first start asks the owner to create one (FirstRunDialog).
        // Tests turn the old behaviour on with this property so they keep a known login.
        if (!Boolean.getBoolean(SEED_DEFAULT_ADMIN_PROPERTY)) {
            LOGGER.info("No accounts yet: the first-run screen will ask the owner to create the administrator.");
            return;
        }

        int employeeId;
        try (var insertEmployee = connection.prepareStatement(
                "INSERT INTO employees (full_name, phone, email, address, position, salary, hire_date, active) " +
                        "VALUES (?, NULL, NULL, NULL, ?, NULL, ?, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            insertEmployee.setString(1, "Quản trị viên hệ thống");
            insertEmployee.setString(2, "Owner");
            insertEmployee.setString(3, AppClock.now().toLocalDate().toString());
            insertEmployee.executeUpdate();
            var keys = insertEmployee.getGeneratedKeys();
            keys.next();
            employeeId = keys.getInt(1);
        }

        try (var insertUser = connection.prepareStatement(
                "INSERT INTO users (username, password_hash, role, status, employee_id, created_at) " +
                        "VALUES (?, ?, ?, " + SqlStatus.ACTIVE + ", ?, ?)")) {
            insertUser.setString(1, "admin");
            insertUser.setString(2, PasswordUtil.hash(DEFAULT_ADMIN_PASSWORD));
            insertUser.setString(3, Role.ADMIN.name());
            insertUser.setInt(4, employeeId);
            insertUser.setString(5, DbTime.format(AppClock.now(), SqlDialect.of(connection)));
            insertUser.executeUpdate();
        }

        // The password itself is deliberately not logged (the log is a file now).
        LOGGER.info("Seeded default admin account (username: admin) with the default password. " +
                "Change this password after first login.");
    }

    private static void seedDiningTables(Connection connection) throws SQLException {
        String flagName = "dining_tables_v1";
        ensureFlagsTable(connection);
        try (var check = connection.prepareStatement("SELECT 1 FROM app_flags WHERE name = ?")) {
            check.setString(1, flagName);
            try (var rs = check.executeQuery()) {
                if (rs.next()) {
                    return; // already seeded once; never re-seed if the user removed tables
                }
            }
        }

        SqlDialect dialect = SqlDialect.of(connection);
        String insertSql = dialect.insertIgnoreInto("dining_tables") + " (table_number, name, status, capacity) VALUES (?, ?, " + SqlStatus.EMPTY + ", ?)";
        try (var insert = connection.prepareStatement(insertSql)) {
            for (int i = 1; i <= 12; i++) {
                insert.setInt(1, i);
                insert.setString(2, "Bàn " + i);
                insert.setInt(3, 4);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (var insertFlag = connection.prepareStatement(dialect.insertIgnoreInto("app_flags") + " (name) VALUES (?)")) {
            insertFlag.setString(1, flagName);
            insertFlag.executeUpdate();
        }
        LOGGER.info("Seeded 12 dining tables into dining_tables.");
    }
}
