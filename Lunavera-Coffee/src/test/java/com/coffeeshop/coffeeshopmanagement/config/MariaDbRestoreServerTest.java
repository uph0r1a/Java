package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * TODO 1.8: the in-app MariaDB restore on a real server (the {@code _test} database of {@code docs/DATABASE.md} section 10;
 * run with {@code -Dlunavera.test.db=mariadb}). Skipped on SQLite, and skipped when {@code mysqldump} cannot be run, because the
 * dump that is restored is made by the same program a backup uses. The database user also needs the right to create and drop
 * {@code lunavera_test_restore_check}; without it the first test says so and the others are skipped.
 *
 * <p>{@link MariaDbRestoreEngineTest} covers the order of the steps and every refusal on a scripted server; what only a real server
 * can show is here: rows really come back, a failure really leaves the old rows, and the scratch database is really gone.
 */
public class MariaDbRestoreServerTest {

    @BeforeClass
    public static void database() {
        TestDatabaseSupport.ensureReady();
        Assume.assumeTrue("MariaDB-only test (this run uses SQLite)", !DatabaseConfig.isSqlite());
    }

    private Path dump;
    private final List<String> hookCalls = new ArrayList<>();

    @Before
    public void makeADump() throws Exception {
        dump = Files.createTempFile("restore-server", ".sql");
        try {
            DatabaseConfig.backupTo(dump);
        } catch (IOException cannotDump) {
            Assume.assumeTrue("mysqldump cannot be run here: " + cannotDump.getMessage(), false);
        }
    }

    private final MariaDbRestore.Hooks recording = new MariaDbRestore.Hooks() {
        @Override
        public void afterCheck() {
            hookCalls.add("afterCheck");
        }

        @Override
        public void beforeCommit() {
            hookCalls.add("beforeCommit");
        }

        @Override
        public void undoBeforeCommit() {
            hookCalls.add("undo");
        }
    };

    private static String addProduct(String name) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO categories (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
        return name;
    }

    private static boolean categoryExists(String name) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM categories WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private static void removeCategory(String name) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM categories WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    private static int schemaVersion() throws SQLException {
        try (Connection c = DatabaseConfig.getConnection()) {
            return DatabaseConfig.readSchemaVersion(c);
        }
    }

    private static boolean scratchDatabaseExists() throws SQLException {
        String scratch = MariaDbRestore.scratchName(DatabaseConfig.settings().database());
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            ps.setString(1, scratch);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** A name no other test uses, so the tests may share the one database. */
    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    public void theBackedUpRowsComeBackAndLaterOnesAreGone() throws Exception {
        String before = addProduct(unique("restore-before"));
        Path backup = Files.createTempFile("restore-server-2", ".sql");
        DatabaseConfig.backupTo(backup); // this one holds `before`
        String after = addProduct(unique("restore-after"));
        removeCategory(before);
        int version = schemaVersion();
        try {
            MariaDbRestore.Result result = MariaDbRestore.restore(backup, recording);
            assertEquals(version, result.schemaVersion());
            assertTrue("the backed-up row is back", categoryExists(before));
            assertFalse("a row made after the backup is gone", categoryExists(after));
            assertEquals("the live version is kept", version, schemaVersion());
            assertEquals(List.of("afterCheck", "beforeCommit"), hookCalls);
            assertFalse("the scratch database is dropped", scratchDatabaseExists());
        } finally {
            Files.deleteIfExists(backup);
            removeCategory(before);
        }
    }

    @Test
    public void aFileThatStopsInTheMiddleChangesNothing() throws Exception {
        String mark = addProduct(unique("restore-keep"));
        try {
            String text = Files.readString(dump);
            Files.writeString(dump, text.substring(0, text.length() / 2));
            try {
                MariaDbRestore.restore(dump, recording);
                fail("a half file must be refused");
            } catch (IOException expected) {
                assertTrue(expected.getMessage(), !expected.getMessage().isBlank());
            }
            assertTrue("the shop's rows are untouched", categoryExists(mark));
            assertTrue("the safety copy was never asked for", hookCalls.isEmpty());
            assertFalse(scratchDatabaseExists());
        } finally {
            removeCategory(mark);
        }
    }

    @Test
    public void aBackupFromANewerVersionIsRefusedAndNothingChanges() throws Exception {
        String mark = addProduct(unique("restore-newer"));
        try {
            int newer = MariaDbMigrations.LATEST_VERSION + 1;
            // the dump writes the row as "VALUES (1,5)" on one line or, in mariadb-dump 10.11, as "VALUES" and the row on the next
            String original = Files.readString(dump);
            String text = original.replaceAll("(INSERT INTO `schema_version` VALUES\\s*\\(1,)" + MariaDbMigrations.LATEST_VERSION + "(\\))", "$1" + newer + "$2");
            assertTrue("the version row of the dump was found and changed", !text.equals(original));
            Files.writeString(dump, text);
            try {
                MariaDbRestore.restore(dump, recording);
                fail("a newer backup must be refused");
            } catch (IOException expected) {
                assertEquals(I18n.t("backup.newer", newer), expected.getMessage());
            }
            assertTrue(categoryExists(mark));
            assertTrue(hookCalls.isEmpty());
        } finally {
            removeCategory(mark);
        }
    }

    @Test
    public void aTableThisProgramDoesNotKnowIsRefusedAndNothingChanges() throws Exception {
        String mark = addProduct(unique("restore-unknown"));
        try {
            Files.writeString(dump, Files.readString(dump) + "\nCREATE TABLE `secrets` (`id` int);\nINSERT INTO `secrets` VALUES (1);\n");
            try {
                MariaDbRestore.restore(dump, recording);
                fail("an unknown table must be refused");
            } catch (IOException expected) {
                assertEquals(I18n.t("backup.restore.maria.unknowntable", "secrets"), expected.getMessage());
            }
            assertTrue(categoryExists(mark));
        } finally {
            removeCategory(mark);
        }
    }

    @Test
    public void aStatementAnAppDumpNeverHoldsIsRefusedBeforeItRuns() throws Exception {
        String mark = addProduct(unique("restore-evil"));
        try {
            Files.writeString(dump, Files.readString(dump) + "\nDELETE FROM `categories`;\n");
            try {
                MariaDbRestore.restore(dump, recording);
                fail("a DELETE in the file must be refused");
            } catch (IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("DELETE"));
            }
            assertTrue("and it did not run", categoryExists(mark));
        } finally {
            removeCategory(mark);
        }
    }

    @Test
    public void aFailureBeforeTheCommitLeavesTheOldRowsInPlace() throws Exception {
        String mark = addProduct(unique("restore-rollback"));
        MariaDbRestore.Hooks failing = new MariaDbRestore.Hooks() {
            @Override
            public void afterCheck() {
                hookCalls.add("afterCheck");
            }

            @Override
            public void beforeCommit() throws Exception {
                hookCalls.add("beforeCommit");
                throw new IOException("the pictures could not be swapped");
            }

            @Override
            public void undoBeforeCommit() {
                hookCalls.add("undo");
            }
        };
        try {
            try {
                MariaDbRestore.restore(dump, failing);
                fail("the restore must fail with its hook");
            } catch (IOException expected) {
                assertEquals("the hook's own message", "the pictures could not be swapped", expected.getMessage());
            }
            // the rows were deleted and copied inside the transaction; the rollback means the shop still has what it had
            assertTrue("the row made after the dump is still there: nothing was committed", categoryExists(mark));
            assertEquals(List.of("afterCheck", "beforeCommit", "undo"), hookCalls);
            assertFalse(scratchDatabaseExists());
        } finally {
            removeCategory(mark);
        }
    }

    @Test
    public void aSecondRestoreWhileOneRunsIsRefusedAsBusy() throws Exception {
        try (Connection other = DatabaseConfig.getConnection()) {
            assertTrue(ServerLock.acquire(other, DatabaseConfig.MIGRATION_LOCK, 0));
            try {
                MariaDbRestore.restore(dump, recording);
                fail("the lock is held, so the restore must not start");
            } catch (IOException expected) {
                assertEquals(I18n.t("backup.restore.maria.busy"), expected.getMessage());
            } finally {
                ServerLock.release(other, DatabaseConfig.MIGRATION_LOCK);
            }
        }
        assertTrue(hookCalls.isEmpty());
    }

    @Test
    public void aScratchDatabaseThatBelongsToSomebodyElseIsNeverTouched() throws Exception {
        String scratch = MariaDbRestore.scratchName(DatabaseConfig.settings().database());
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            try {
                st.execute("CREATE DATABASE IF NOT EXISTS `" + scratch + "`");
                st.execute("CREATE TABLE `" + scratch + "`.`somebody_elses` (id INT)");
            } catch (SQLException noRight) {
                Assume.assumeTrue("the database user may not create " + scratch, false);
            }
            try {
                MariaDbRestore.check(dump);
                fail("a database that is not ours must stop the check");
            } catch (IOException expected) {
                assertEquals(I18n.t("backup.restore.maria.foreign", scratch), expected.getMessage());
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + scratch + "`.`somebody_elses`")) {
                rs.next();
                assertEquals("their table is still there", 0, rs.getInt(1));
            } finally {
                st.execute("DROP DATABASE `" + scratch + "`");
            }
        }
    }
}
