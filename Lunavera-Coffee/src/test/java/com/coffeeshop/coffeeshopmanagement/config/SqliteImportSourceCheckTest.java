package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.62: before the SQLite-to-MariaDB import copies a row, the old file is opened read-only and looked at: its version, its health
 * and its foreign keys. Every case here is a small hand-made file, so no MariaDB server is needed.
 */
public class SqliteImportSourceCheckTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** A SQLite file with a {@code users} table (so it passes the "is it Lunavera's" test) and the given extra statements. */
    private Path newFile(int userVersion, String... statements) throws SQLException, IOException {
        Path file = folder.newFile().toPath();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)");
            for (String sql : statements) {
                st.execute(sql);
            }
            st.execute("PRAGMA user_version = " + userVersion);
        }
        return file;
    }

    private static SqliteToMariaDbImporter.SourceCheck check(Path file) throws SQLException {
        try (Connection c = SqliteToMariaDbImporter.openSourceReadOnly(file)) {
            return SqliteToMariaDbImporter.checkSource(c);
        }
    }

    @Test
    public void aFileOfTheLatestVersionWithNothingWrongPassesWithoutAWarning() throws Exception {
        SqliteToMariaDbImporter.SourceCheck result = check(newFile(SchemaMigrations.LATEST_VERSION));
        assertNull(result.problem());
        assertNull(result.warning());
    }

    @Test
    public void aFileFromAnOlderVersionThatIsNotZeroPassesWithoutAWarning() throws Exception {
        SqliteToMariaDbImporter.SourceCheck result = check(newFile(1));
        assertNull(result.problem());
        assertNull(result.warning());
    }

    @Test
    public void aFileFromANewerVersionIsRefusedAndTheVersionIsNamed() throws Exception {
        int newer = SchemaMigrations.LATEST_VERSION + 1;
        SqliteToMariaDbImporter.SourceCheck result = check(newFile(newer));
        assertNotNull(result.problem());
        assertTrue(result.problem(), result.problem().contains("v" + newer));
        assertTrue(result.problem(), result.problem().contains("v" + SchemaMigrations.LATEST_VERSION));
    }

    @Test
    public void versionZeroIsCopiedButWarnedAbout() throws Exception {
        SqliteToMariaDbImporter.SourceCheck result = check(newFile(0));
        assertNull(result.problem());
        assertNotNull(result.warning());
        assertTrue(result.warning(), result.warning().contains("version 0"));
    }

    @Test
    public void aDamagedFileIsRefusedInPlainWords() throws Exception {
        Path file = folder.newFile().toPath();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)");
            }
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO users (name) VALUES (?)")) {
                String text = "x".repeat(100);
                for (int i = 0; i < 400; i++) {
                    insert.setString(1, text + i);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            c.commit();
            c.setAutoCommit(true);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA user_version = " + SchemaMigrations.LATEST_VERSION);
            }
        }
        // The third 4096-byte page is a leaf of the users table (page 1 holds the schema, page 2 the root): wipe it.
        byte[] bytes = Files.readAllBytes(file);
        assertTrue("the file must have several pages for this test", bytes.length >= 4 * 4096);
        Arrays.fill(bytes, 2 * 4096, 3 * 4096, (byte) 0);
        Files.write(file, bytes);

        SqliteToMariaDbImporter.SourceCheck result = check(file);
        assertNotNull(result.problem());
        assertTrue("the owner is not shown SQLite's own text: " + result.problem(),
                !result.problem().toLowerCase(java.util.Locale.ROOT).contains("malformed") && !result.problem().contains("btree"));
    }

    @Test
    public void rowsThatPointAtNothingAreRefusedAndTheFirstOnesAreNamed() throws Exception {
        Path file = newFile(SchemaMigrations.LATEST_VERSION,
                "CREATE TABLE customers (id INTEGER PRIMARY KEY)",
                "CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER, FOREIGN KEY (customer_id) REFERENCES customers(id))",
                "INSERT INTO customers (id) VALUES (1)",
                "INSERT INTO orders (id, customer_id) VALUES (1, 1)",
                "INSERT INTO orders (id, customer_id) VALUES (2, 77)",
                "INSERT INTO orders (id, customer_id) VALUES (3, 78)",
                "INSERT INTO orders (id, customer_id) VALUES (4, 79)",
                "INSERT INTO orders (id, customer_id) VALUES (5, 80)",
                "INSERT INTO orders (id, customer_id) VALUES (6, 81)",
                "INSERT INTO orders (id, customer_id) VALUES (7, 82)",
                "INSERT INTO orders (id, customer_id) VALUES (8, 83)");
        SqliteToMariaDbImporter.SourceCheck result = check(file);
        assertNotNull(result.problem());
        assertTrue(result.problem(), result.problem().contains("7"));            // the number of all of them
        assertTrue(result.problem(), result.problem().contains("orders #"));
        assertTrue(result.problem(), result.problem().contains("-> customers"));
        assertTrue("only the first five are named: " + result.problem(),
                result.problem().split("orders #", -1).length - 1 == 5);
    }

    @Test
    public void aFileWhoseRowsAllPointAtSomethingPasses() throws Exception {
        Path file = newFile(SchemaMigrations.LATEST_VERSION,
                "CREATE TABLE customers (id INTEGER PRIMARY KEY)",
                "CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER, FOREIGN KEY (customer_id) REFERENCES customers(id))",
                "INSERT INTO customers (id) VALUES (1)",
                "INSERT INTO orders (id, customer_id) VALUES (1, 1)",
                "INSERT INTO orders (id, customer_id) VALUES (2, NULL)");
        SqliteToMariaDbImporter.SourceCheck result = check(file);
        assertNull(result.problem());
    }

    @Test
    public void theOldFileIsOpenedReadOnlyAndIsNotChangedByLookingAtIt() throws Exception {
        Path file = newFile(SchemaMigrations.LATEST_VERSION, "INSERT INTO users (name) VALUES ('admin')");
        byte[] before = Files.readAllBytes(file);

        try (Connection c = SqliteToMariaDbImporter.openSourceReadOnly(file)) {
            SQLException refused = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("INSERT INTO users (name) VALUES ('intruder')");
                }
            });
            assertTrue(refused.getMessage(), refused.getMessage().toLowerCase(java.util.Locale.ROOT).contains("readonly")
                    || refused.getMessage().toLowerCase(java.util.Locale.ROOT).contains("read-only"));
            SqliteToMariaDbImporter.checkSource(c);
        }
        assertArrayEquals("the file must be byte for byte as it was", before, Files.readAllBytes(file));
    }
}
