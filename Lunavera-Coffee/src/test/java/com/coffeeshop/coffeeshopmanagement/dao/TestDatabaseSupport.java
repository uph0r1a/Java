package com.coffeeshop.coffeeshopmanagement.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All DAO tests share one throwaway SQLite database for the whole test JVM run.
 * {@code DatabaseConfig.DB_URL} is a {@code static final} field resolved the first time that
 * class is loaded, from the {@code lunavera.dataDir} system property at that moment - so
 * that property (and {@code user.home}) must point at a fresh temp directory *before* anything touches
 * {@code DatabaseConfig}, and every DAO test class must go through this one shared bootstrap
 * rather than each pointing at its own directory (only whichever test class happened to run
 * first would actually take effect; the rest would silently share its database instead of
 * getting their own). Reading {@link #TEMP_HOME} from a test's {@code @BeforeClass} forces this
 * static initializer to run before that test ever calls a real {@code DatabaseConfig} method.
 *
 * Because every DAO test shares this one database for the run, tests here don't rely on the
 * table being empty (no assertions like "findAll().size() == 1") - each test creates its own
 * uniquely-named row(s) (a random suffix) and only asserts about the row it created. The whole
 * temp directory is deleted by a shutdown hook when the test JVM ends (TODO 6.61: it holds about 3 MB of starter photos
 * and used to pile up in the system temp folder, one per run), so there is no delete-after-test bookkeeping needed even
 * for DAOs (like CustomerDAO) with no delete method at all.
 */
public final class TestDatabaseSupport {

    public static final Path TEMP_HOME;

    static {
        try {
            TEMP_HOME = Files.createTempDirectory("lunavera-dao-test-home");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(TEMP_HOME), "lunavera-test-home-cleanup"));
            // user.home too: the one-time copy from ~/.lunavera-coffee must never find a real home folder.
            System.setProperty("user.home", TEMP_HOME.toString());
            System.setProperty("lunavera.dataDir", TEMP_HOME.resolve("data").toString());
            // Production seeds no administrator (TODO 2A.5, 2A.33); the tests insert a known login themselves, see seedTestAdmin().
            System.setProperty("lunavera.db.type", "sqlite"); // the app default is MariaDB; tests need no server
            configureMariaDbIfRequested();
            // One date for the whole run (TODO 6.39): the data code and the tests both read AppClock, so a run that crosses
            // midnight cannot see "today" change under a test.
            com.coffeeshop.coffeeshopmanagement.util.AppClock.pinDate(java.time.LocalDate.now());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestDatabaseSupport() {
    }

    /** Best effort: a file still open (SQLite on Windows) is left behind, it must never fail the run. */
    private static void deleteTree(Path root) {
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // nothing to do: the system temp folder is cleaned by the system sooner or later
                }
            });
        } catch (IOException | RuntimeException ignored) {
            // the folder may already be gone
        }
    }

    /**
     * {@code mvn test -Dlunavera.test.db=mariadb} runs every database test on a real MariaDB/MySQL server instead of
     * SQLite (optional: {@code -Dlunavera.test.db.host / .port / .name / .user / .password}; defaults 127.0.0.1, 3306,
     * lunavera_test, lunavera_test, empty). <b>Every table in that database is dropped first</b>, so its name must end in
     * {@code _test}; anything else is refused, so a run can never wipe the shop's real database.
     */
    private static void configureMariaDbIfRequested() {
        if (!"mariadb".equalsIgnoreCase(System.getProperty("lunavera.test.db"))) {
            return;
        }
        String host = System.getProperty("lunavera.test.db.host", "127.0.0.1");
        String port = System.getProperty("lunavera.test.db.port", "3306");
        String name = System.getProperty("lunavera.test.db.name", "lunavera_test");
        String user = System.getProperty("lunavera.test.db.user", "lunavera_test");
        String password = System.getProperty("lunavera.test.db.password", "");
        if (!name.endsWith("_test")) {
            throw new IllegalStateException("Refusing to run the tests on database \"" + name
                    + "\": every table in it is dropped first, so its name must end with _test.");
        }
        System.setProperty("lunavera.db.type", "mariadb");
        System.setProperty("lunavera.db.host", host);
        System.setProperty("lunavera.db.port", port);
        System.setProperty("lunavera.db.name", name);
        System.setProperty("lunavera.db.user", user);
        System.setProperty("lunavera.db.password", password);
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + name;
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(url, user, password);
             java.sql.Statement st = c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : com.coffeeshop.coffeeshopmanagement.config.MariaDbSchema.DROP_ORDER) {
                st.execute("DROP TABLE IF EXISTS " + table);
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot prepare the MariaDB test database " + url + ": " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ days of a test's own (TODO 6.77)

    /** The first day handed out by {@link #freshDays}: far beyond every date a test writes by hand (the latest is in the 2400s). */
    private static final int FIRST_FRESH_YEAR = 3000;
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_FRESH_DAY = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * The first of {@code count} consecutive days that no other test of this run has been given, so a test that writes sales,
     * expenses, clock-ins or bookings on them and then counts over a range sees only its own rows. The days are in the year 3000
     * and after (both databases keep dates that far), a gap of a week is left after every block so a range that is a day too wide
     * still touches nobody else, and a call from any test class or in any order gets a block of its own. Prefer this to a year chosen
     * by hand: two classes both claiming 2032 as their own was exactly the fault ({@code TestYearsAreOwnedTest}).
     */
    public static java.time.LocalDate freshDays(int count) {
        if (count < 1) throw new IllegalArgumentException("count must be at least 1");
        int offset = NEXT_FRESH_DAY.getAndAdd(count + 7);
        return java.time.LocalDate.of(FIRST_FRESH_YEAR, 1, 1).plusDays(offset);
    }

    /** {@link #freshDays(int)} for a test that needs just one day. */
    public static java.time.LocalDate freshDay() {
        return freshDays(1);
    }

    // ------------------------------------------------------------------ putting tables back (TODO 6.77)

    /**
     * The rows of some small tables as they were, so a test that changes them (the table list, the settings, the vouchers) can put
     * them back in an {@code @After} and the next test class sees what it expected, whatever order the classes run in:
     * <pre>
     *   private TestDatabaseSupport.Snapshot before;
     *   &#64;Before public void remember() { before = TestDatabaseSupport.snapshot("settings", "dining_tables"); }
     *   &#64;After  public void putBack()  { before.restore(); }
     * </pre>
     * Name a parent table before the tables that point at it. Not for a table with a generated column ({@code orders},
     * {@code time_clock}: their rows are made by the program) or with a lot of rows: it reads them all into memory.
     */
    public static Snapshot snapshot(String... tables) {
        Snapshot snapshot = new Snapshot();
        try (java.sql.Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection()) {
            for (String table : tables) {
                snapshot.tables.add(Snapshot.read(c, table));
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Could not read the tables to put back later: " + java.util.Arrays.toString(tables), e);
        }
        return snapshot;
    }

    /** What {@link #snapshot} took; {@link #restore()} deletes whatever the tables hold now and writes the old rows back. */
    public static final class Snapshot {
        private record Rows(String table, java.util.List<String> columns, java.util.List<Object[]> rows) { }

        private final java.util.List<Rows> tables = new java.util.ArrayList<>();

        private static Rows read(java.sql.Connection c, String table) throws java.sql.SQLException {
            if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("not a table name: " + table);
            try (java.sql.Statement st = c.createStatement(); java.sql.ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
                java.sql.ResultSetMetaData meta = rs.getMetaData();
                java.util.List<String> columns = new java.util.ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnName(i));
                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                while (rs.next()) {
                    Object[] row = new Object[columns.size()];
                    for (int i = 0; i < row.length; i++) row[i] = rs.getObject(i + 1);
                    rows.add(row);
                }
                return new Rows(table, columns, rows);
            }
        }

        public void restore() {
            try (java.sql.Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection()) {
                boolean auto = c.getAutoCommit();
                c.setAutoCommit(false);
                try {
                    // children first when deleting, parents first when writing back
                    for (int i = tables.size() - 1; i >= 0; i--) {
                        try (java.sql.Statement st = c.createStatement()) {
                            st.executeUpdate("DELETE FROM " + tables.get(i).table());
                        }
                    }
                    for (Rows t : tables) {
                        if (t.rows().isEmpty()) continue;
                        String marks = String.join(", ", java.util.Collections.nCopies(t.columns().size(), "?"));
                        String sql = "INSERT INTO " + t.table() + " (" + String.join(", ", t.columns()) + ") VALUES (" + marks + ")";
                        try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                            for (Object[] row : t.rows()) {
                                for (int i = 0; i < row.length; i++) ps.setObject(i + 1, row[i]);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                    }
                    c.commit();
                } catch (java.sql.SQLException | RuntimeException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(auto);
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Could not put the tables back", e);
            }
        }
    }

    /** Skips the calling test when the run uses MariaDB: for tests about SQLite files, PRAGMAs and the SQLite-only migrations. */
    public static void requireSqlite() {
        org.junit.Assume.assumeTrue("SQLite-only test (this run uses MariaDB)",
                com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite());
    }

    /** Call from @BeforeClass: touches TEMP_HOME (running the static block above if it hasn't
     *  run yet) then initializes the schema - safe to call from every DAO test class, since
     *  DatabaseConfig.initialize() is itself idempotent. */
    public static void ensureReady() {
        Object forceStaticInit = TEMP_HOME;
        // Safety net: if some test loaded DatabaseConfig before the temp folder was set, every database
        // test in this run would be writing into the real data/ folder. Stop instead of polluting it.
        if (com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite()) {
            Path live = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.databaseFile().toAbsolutePath().normalize();
            // The folders a program started without any setting would use: the project's data/ and (TODO 3.69) the fixed per-user folder.
            Path realData = Path.of(System.getProperty("user.dir"), "data").toAbsolutePath().normalize();
            Path realDefault = com.coffeeshop.coffeeshopmanagement.util.DataPaths.defaultDir();
            boolean touchesReal = live.startsWith(realData) || live.startsWith(realDefault);
            if (touchesReal && !TEMP_HOME.toAbsolutePath().normalize().startsWith(realData) && !TEMP_HOME.toAbsolutePath().normalize().startsWith(realDefault)) {
                throw new IllegalStateException("A test started DatabaseConfig before the temp data folder was set; "
                        + "it would use the real database " + live + ". Call TestDatabaseSupport.ensureReady() "
                        + "(or touch TestDatabaseSupport.TEMP_HOME) in that test's @BeforeClass.");
            }
        } else {
            com.coffeeshop.coffeeshopmanagement.config.DatabaseSettings settings =
                    com.coffeeshop.coffeeshopmanagement.config.DatabaseSettings.load();
            if (!settings.database().endsWith("_test")) {
                throw new IllegalStateException("A MariaDB test run must use a database whose name ends with _test, but this one is "
                        + settings.describe());
            }
        }
        com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.initialize();
        seedTestAdmin();
    }

    /** When the database has no account at all, adds {@code admin} / {@code DEFAULT_ADMIN_PASSWORD} (ACTIVE, role ADMIN) with an employee. */
    static void seedTestAdmin() {
        try (java.sql.Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection()) {
            try (java.sql.Statement st = c.createStatement(); java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                if (rs.getInt(1) > 0) return;
            }
            String now = com.coffeeshop.coffeeshopmanagement.util.DbTime.format(com.coffeeshop.coffeeshopmanagement.util.AppClock.now(),
                    com.coffeeshop.coffeeshopmanagement.config.SqlDialect.of(c));
            int employeeId;
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO employees (full_name, phone, email, address, position, salary, hire_date, active) VALUES (?, NULL, NULL, NULL, ?, NULL, ?, 1)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "Quản trị viên hệ thống");
                ps.setString(2, "Owner");
                ps.setString(3, com.coffeeshop.coffeeshopmanagement.util.AppClock.now().toLocalDate().toString());
                ps.executeUpdate();
                try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    employeeId = keys.getInt(1);
                }
            }
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users (username, password_hash, role, status, employee_id, created_at) VALUES (?, ?, ?, "
                            + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.ACTIVE + ", ?, ?)")) {
                ps.setString(1, "admin");
                ps.setString(2, com.coffeeshop.coffeeshopmanagement.service.PasswordUtil.hash(
                        com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.DEFAULT_ADMIN_PASSWORD));
                ps.setString(3, com.coffeeshop.coffeeshopmanagement.model.Role.ADMIN.name());
                ps.setInt(4, employeeId);
                ps.setString(5, now);
                ps.executeUpdate();
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Could not add the test administrator", e);
        }
    }
}
