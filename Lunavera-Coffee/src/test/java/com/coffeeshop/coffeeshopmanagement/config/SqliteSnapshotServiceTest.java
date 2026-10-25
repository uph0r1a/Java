package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Copies the test database (SQLite, or MariaDB in a MariaDB test run) into a snapshot file and checks the file. */
public class SqliteSnapshotServiceTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 11, 20, 7);

    private static int count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Connection open(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }

    @Test
    public void everyTableIsCopiedWithTheSameNumberOfRowsAndTheSameValues() throws Exception {
        Customer customer = new Customer();
        customer.setFullName("Nguyễn Văn Ánh " + UUID.randomUUID());
        customer.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100_000_000));
        customer.setLoyaltyPoints(0);
        new CustomerDAO().insert(customer);
        Path dir = Files.createTempDirectory("lunavera-snapshot-test");

        Path snapshot = SqliteSnapshotService.createSnapshot(DatabaseConfig::getConnection, dir, NOW, 10);

        assertEquals("lunavera-20261013-112007.db", snapshot.getFileName().toString());
        assertFalse("no half-written file is left", Files.exists(dir.resolve(snapshot.getFileName() + ".partial")));
        try (Connection source = DatabaseConfig.getConnection(); Connection copy = open(snapshot)) {
            for (SqliteToMariaDbImporter.TableSpec table : SqliteToMariaDbImporter.TABLES) {
                assertEquals("rows of " + table.name(), count(source, table.name()), count(copy, table.name()));
            }
            try (PreparedStatement ps = copy.prepareStatement("SELECT full_name, loyalty_points FROM customers WHERE phone = ?")) {
                ps.setString(1, customer.getPhone());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("the new customer is in the snapshot", rs.next());
                    assertEquals(customer.getFullName(), rs.getString(1));
                    assertEquals(0, rs.getInt(2));
                }
            }
        }
    }

    @Test
    public void datesAreStoredTheWayTheSqliteAppStoresThem() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-snapshot-dates");
        Path snapshot = SqliteSnapshotService.createSnapshot(DatabaseConfig::getConnection, dir, NOW, 10);
        try (Connection copy = open(snapshot); Statement st = copy.createStatement();
             ResultSet rs = st.executeQuery("SELECT created_at FROM users LIMIT 1")) {
            assertTrue(rs.next());
            String text = rs.getString(1);
            assertNotNull(text);
            assertTrue("ISO text with a T: " + text, text.contains("T"));
            assertNotNull(DbTime.parse(text));
        }
    }

    @Test
    public void theSnapshotIsACompleteSqliteDatabaseAtTheCurrentVersionWithItsIndexes() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-snapshot-version");
        Path snapshot = SqliteSnapshotService.createSnapshot(DatabaseConfig::getConnection, dir, NOW, 10);
        try (Connection copy = open(snapshot); Statement st = copy.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                rs.next();
                assertEquals(SchemaMigrations.LATEST_VERSION, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name NOT LIKE 'sqlite_%'")) {
                rs.next();
                assertTrue("the indexes were created", rs.getInt(1) >= 1);
            }
        }
    }

    @Test
    public void onlyTheNewestTenSnapshotsRemainAfterTwelveHours() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-snapshot-keep");
        Path newest = null;
        for (int hour = 0; hour < 12; hour++) {
            newest = SqliteSnapshotService.createSnapshot(DatabaseConfig::getConnection, dir, NOW.plusHours(hour), 10);
        }
        try (var files = Files.list(dir)) {
            assertEquals(10, files.filter(p -> p.getFileName().toString().endsWith(".db")).count());
        }
        assertFalse(Files.exists(dir.resolve(SqliteSnapshotService.fileName(NOW))));
        assertFalse(Files.exists(dir.resolve(SqliteSnapshotService.fileName(NOW.plusHours(1)))));
        assertTrue(Files.exists(newest));
    }

    @Test
    public void aFailureLeavesNoFileAndDeletesNothing() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-snapshot-fail");
        Path good = SqliteSnapshotService.createSnapshot(DatabaseConfig::getConnection, dir, NOW, 10);
        try {
            SqliteSnapshotService.createSnapshot(() -> {
                throw new SQLException("server down");
            }, dir, NOW.plusHours(1), 1); // keep=1 would delete 'good' if the failed run pruned
            fail("a failing source must be reported");
        } catch (SQLException expected) {
            assertEquals("server down", expected.getMessage());
        }
        try (var files = Files.list(dir)) {
            assertEquals("only the good snapshot, no .partial", 1, files.count());
        }
        assertTrue(Files.exists(good));
    }

    @Test
    public void startDoesNothingOnASqliteShop() throws IOException {
        if (!DatabaseConfig.isSqlite()) {
            return; // a MariaDB test run: start() would legitimately begin the hourly job
        }
        SqliteSnapshotService.start(); // must return at once and create no folder
        assertFalse(Files.exists(com.coffeeshop.coffeeshopmanagement.util.DataPaths.snapshotsDir()));
    }
}
