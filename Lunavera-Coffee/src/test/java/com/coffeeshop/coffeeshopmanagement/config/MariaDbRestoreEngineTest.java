package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static com.coffeeshop.coffeeshopmanagement.config.FakeServer.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * TODO 1.8: the order of the steps of the MariaDB restore, what is rolled back and what is refused, on a scripted stand-in for
 * the server ({@link FakeServer}). No server is needed; {@code MariaDbRestoreServerTest} does the same on a real one.
 */
public class MariaDbRestoreEngineTest {

    private static final String SHOP = "lunavera_test";
    private static final String SCRATCH = SHOP + "_restore_check";

    private Path dump;
    private FakeServer server;
    private Hooks hooks;

    /** Records what the caller's hooks are asked to do, in the same log as the statements. */
    private final class Hooks implements MariaDbRestore.Hooks {
        boolean failAfterCheck;
        boolean failBeforeCommit;

        @Override
        public void afterCheck() throws Exception {
            server.log.add("HOOK afterCheck");
            if (failAfterCheck) throw new IOException("no safety copy");
        }

        @Override
        public void beforeCommit() throws Exception {
            server.log.add("HOOK beforeCommit");
            if (failBeforeCommit) throw new IOException("pictures stuck");
        }

        @Override
        public void undoBeforeCommit() {
            server.log.add("HOOK undo");
        }
    }

    @Before
    public void setUp() throws IOException {
        dump = Files.createTempFile("restore-engine", ".sql");
        Files.writeString(dump, """
                DROP TABLE IF EXISTS `users`;
                CREATE TABLE `users` (`id` int NOT NULL, `username` varchar(50), `role` varchar(20), `status` varchar(20), PRIMARY KEY (`id`));
                INSERT INTO `users` VALUES (1,'owner','ADMIN','ACTIVE');
                CREATE TABLE `products` (`id` int NOT NULL, `name` varchar(50), PRIMARY KEY (`id`));
                INSERT INTO `products` VALUES (1,'Latte'),(2,'Tea');
                CREATE TABLE `orders` (`id` int NOT NULL, `order_date` datetime, PRIMARY KEY (`id`));
                INSERT INTO `orders` VALUES (1,'2026-10-30 12:00:00');
                CREATE TABLE `schema_version` (`id` tinyint NOT NULL, `version` int NOT NULL, PRIMARY KEY (`id`));
                INSERT INTO `schema_version` VALUES (1,3);
                """);
        server = happyServer();
        hooks = new Hooks();
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(dump);
    }

    private static final Object[][] SCRATCH_COLUMNS = {
            row("lunavera_restore_marker", "id"),
            row("orders", "id"), row("orders", "order_date"),
            row("products", "id"), row("products", "name"),
            row("schema_version", "id"), row("schema_version", "version"),
            row("users", "id"), row("users", "username"), row("users", "role"), row("users", "status")};

    /** The live shop has everything the backup has, plus `orders.rev` (a newer column) and two more tables. */
    private static final Object[][] LIVE_COLUMNS = {
            row("orders", "id"), row("orders", "order_date"), row("orders", "rev"),
            row("products", "id"), row("products", "name"),
            row("schema_version", "id"), row("schema_version", "version"),
            row("users", "id"), row("users", "username"), row("users", "role"), row("users", "status"),
            row("audit_log", "id"), row("not_ours", "id")};

    /** A server where the lock is free, the scratch name is free and the backup is good; a test changes one thing with the helpers below. */
    private FakeServer server(int version, long admins, Object[][] scratchColumns, Object[][] liveColumns) {
        return new FakeServer(SHOP)
                .rule("GET_LOCK", null, row(1))
                .rule("RELEASE_LOCK", null, row(1))
                .rule("SELECT COUNT(*) FROM information_schema.TABLES", SCRATCH, row(0))
                .rule("SELECT TABLE_NAME FROM information_schema.TABLES", SHOP,
                        row("users"), row("products"), row("orders"), row("schema_version"), row("audit_log"), row("not_ours"))
                .rule("FROM information_schema.COLUMNS", SCRATCH, scratchColumns)
                .rule("FROM information_schema.COLUMNS", SHOP, liveColumns)
                .rule("SELECT version FROM schema_version", null, row(version))
                .rule("SELECT COUNT(*) FROM `orders`", null, row(1))
                .rule("SELECT COUNT(*) FROM `products`", null, row(2))
                .rule("SELECT COUNT(*) FROM `users`", null, row(1))
                .rule("SELECT COUNT(*) FROM users WHERE role", null, row(admins))
                .rule("KEY_COLUMN_USAGE", SCRATCH)
                .rule("SELECT MAX(order_date)", null, row("2026-10-30 12:00:00"));
    }

    private FakeServer happyServer() {
        return server(3, 1, SCRATCH_COLUMNS, LIVE_COLUMNS);
    }

    private static Object[][] without(Object[][] columns, String table) {
        return java.util.Arrays.stream(columns).filter(c -> !c[0].equals(table)).toArray(Object[][]::new);
    }

    private static Object[][] with(Object[][] columns, Object[]... more) {
        List<Object[]> all = new java.util.ArrayList<>(java.util.Arrays.asList(columns));
        all.addAll(java.util.Arrays.asList(more));
        return all.toArray(Object[][]::new);
    }

    private MariaDbRestore.Result restore() throws IOException {
        return MariaDbRestore.restore(server, dump, hooks);
    }

    private IOException refused() {
        try {
            restore();
            fail("the restore should have been refused");
            return null;
        } catch (IOException expected) {
            return expected;
        }
    }

    private void assertNothingOfTheShopChanged() {
        assertFalse(server.log.toString(), server.ran("DELETE FROM"));
        assertFalse(server.ran("COMMIT"));
        assertFalse("the safety copy is not even asked for", server.ran("HOOK afterCheck"));
        assertTrue("the scratch database is dropped", server.ran("DROP DATABASE IF EXISTS `" + SCRATCH + "`"));
        assertTrue("the lock is let go", server.ran("RELEASE_LOCK"));
    }

    // ---- the good path

    @Test
    public void theStepsRunInTheOrderThatProtectsTheShop() throws Exception {
        MariaDbRestore.Result result = restore();
        assertEquals(3, result.schemaVersion());
        int lock = server.indexOf("GET_LOCK");
        int create = server.indexOf("CREATE DATABASE `" + SCRATCH + "`");
        int loaded = server.indexOf("INSERT INTO `orders` VALUES");
        int safety = server.indexOf("HOOK afterCheck");
        int deleted = server.indexOf("DELETE FROM");
        int copied = server.indexOf("FROM `" + SCRATCH + "`.`orders`");
        int pictures = server.indexOf("HOOK beforeCommit");
        int commit = server.indexOf("COMMIT");
        int dropped = server.indexOf("DROP DATABASE IF EXISTS `" + SCRATCH + "`", commit);
        int release = server.indexOf("RELEASE_LOCK");
        assertTrue(server.log.toString(), 0 <= lock && lock < create && create < loaded && loaded < safety && safety < deleted
                && deleted < copied && copied < pictures && pictures < commit && commit < dropped && dropped < release);
        assertFalse("never rolled back", server.ran("ROLLBACK"));
    }

    @Test
    public void onlyTheAppsOwnTablesAreEmptiedAndTheVersionTableIsLeftAlone() throws Exception {
        restore();
        assertTrue(server.ran("DELETE FROM `users`"));
        assertTrue(server.ran("DELETE FROM `products`"));
        assertTrue(server.ran("DELETE FROM `orders`"));
        assertTrue(server.ran("DELETE FROM `audit_log`")); // a table the backup lacks is emptied too: it was empty then
        assertFalse("a table that is not the app's is never touched", server.ran("`not_ours`"));
        for (String line : server.log) { // the dump's own version row goes into the scratch database only
            assertFalse("the live version stays: " + line, line.startsWith("live") && line.contains("`schema_version`"));
        }
        assertFalse("the live structure is never changed", server.ran("DROP TABLE") || server.ran("TRUNCATE") || server.ran("RENAME")
                || server.ran("ALTER"));
    }

    @Test
    public void aColumnTheBackupDoesNotHaveTakesItsDefault() throws Exception {
        restore();
        assertTrue(server.ran("INSERT INTO `orders` (`id`, `order_date`) SELECT `id`, `order_date` FROM"));
        assertFalse("orders.rev is in the live table only", server.ran("`rev`"));
    }

    @Test
    public void theDumpIsRunOnlyInTheScratchDatabaseAndItsOwnDropIsSkipped() throws Exception {
        restore();
        for (String line : server.log) {
            if (line.contains("INSERT INTO `orders` VALUES") || line.contains("CREATE TABLE `users`")) {
                assertTrue(line, line.startsWith("scratch"));
            }
            assertFalse("the dump's own DROP TABLE must not be run: " + line, line.contains("DROP TABLE IF EXISTS `users`"));
        }
    }

    @Test
    public void aPlainCheckReadsTheNumbersAndChangesNothingOfTheShop() throws Exception {
        MariaDbRestore.Checked checked = MariaDbRestore.check(server, dump);
        assertEquals(1, checked.orders());
        assertEquals(2, checked.products());
        assertEquals("2026-10-30 12:00", checked.newestOrder());
        assertEquals(3, checked.schemaVersion());
        assertFalse(server.ran("DELETE FROM"));
        assertFalse(server.ran("COMMIT"));
        assertTrue("the scratch database is dropped again", server.count("DROP DATABASE IF EXISTS `" + SCRATCH + "`") >= 2);
        assertTrue(server.ran("RELEASE_LOCK"));
    }

    // ---- refused while only the scratch database has been touched

    @Test
    public void aBackupFromANewerVersionIsRefused() {
        server = server(MariaDbMigrations.LATEST_VERSION + 1, 1, SCRATCH_COLUMNS, LIVE_COLUMNS);
        assertEquals(I18n.t("backup.newer", MariaDbMigrations.LATEST_VERSION + 1), refused().getMessage());
        assertNothingOfTheShopChanged();
    }

    @Test
    public void aBackupWithoutAVersionTableIsNotOurs() {
        server = server(3, 1, without(SCRATCH_COLUMNS, "schema_version"), LIVE_COLUMNS);
        assertEquals(I18n.t("backup.restore.maria.notlunavera"), refused().getMessage());
        assertNothingOfTheShopChanged();
    }

    @Test
    public void aBackupWhoseVersionIsZeroIsNotOurs() {
        server = server(0, 1, SCRATCH_COLUMNS, LIVE_COLUMNS);
        assertEquals(I18n.t("backup.restore.maria.notlunavera"), refused().getMessage());
        assertNothingOfTheShopChanged();
    }

    @Test
    public void aBackupWithoutACoreTableIsRefused() {
        server = server(3, 1, without(SCRATCH_COLUMNS, "orders"), LIVE_COLUMNS);
        assertEquals(I18n.t("backup.notdb", "orders"), refused().getMessage());
        assertNothingOfTheShopChanged();
    }

    @Test
    public void aTableThisProgramDoesNotKnowIsRefusedWhileTheFileIsRead() throws Exception {
        Files.writeString(dump, "CREATE TABLE `secrets` (`id` int);\nINSERT INTO `secrets` VALUES (1);\n");
        assertEquals(I18n.t("backup.restore.maria.unknowntable", "secrets"), refused().getMessage());
        assertFalse("never run", server.ran("secrets"));
        assertNothingOfTheShopChanged();
    }

    @Test
    public void aTableTheLiveDatabaseLacksIsRefused() {
        server = server(3, 1, with(SCRATCH_COLUMNS, row("refunds", "id")), LIVE_COLUMNS);
        assertEquals(I18n.t("backup.restore.maria.unknowntable", "refunds"), refused().getMessage());
        assertNothingOfTheShopChanged();
    }

    @Test
    public void aColumnThisProgramDoesNotKnowIsRefused() {
        server = server(3, 1, with(SCRATCH_COLUMNS, row("products", "from_the_future")), LIVE_COLUMNS);
        assertEquals(I18n.t("backup.restore.maria.unknowncolumn", "products", "from_the_future"), refused().getMessage());
        assertNothingOfTheShopChanged();
    }

    @Test
    public void aBackupWithoutAnActiveAdministratorIsRefused() {
        server = server(3, 0, SCRATCH_COLUMNS, LIVE_COLUMNS);
        assertEquals(I18n.t("backup.noadmin"), refused().getMessage());
        assertNothingOfTheShopChanged();
    }

    @Test
    public void rowsThatPointAtMissingRowsAreRefused() {
        server = new FakeServer(SHOP)
                .rule("GET_LOCK", null, row(1)).rule("RELEASE_LOCK", null, row(1))
                .rule("SELECT COUNT(*) FROM information_schema.TABLES", SCRATCH, row(0))
                .rule("FROM information_schema.COLUMNS", SCRATCH, with(SCRATCH_COLUMNS, row("orders", "customer_id")))
                .rule("FROM information_schema.COLUMNS", SHOP, with(LIVE_COLUMNS, row("orders", "customer_id")))
                .rule("SELECT version FROM schema_version", null, row(3))
                .rule("WHERE `customer_id` IS NOT NULL", null, row(7))
                .rule("SELECT COUNT(*) FROM `", null, row(1))
                .rule("SELECT COUNT(*) FROM users WHERE role", null, row(1))
                // orders.customer_id points at customers.id, and this backup has no customers table: every non-null value is an orphan
                .rule("KEY_COLUMN_USAGE", SCRATCH, row("orders", "customer_id", "customers", "id"));
        assertEquals(I18n.t("backup.restore.maria.orphans", "orders", 7L), refused().getMessage());
        assertNothingOfTheShopChanged();
    }

    @Test
    public void whenTheCheckDatabaseIsNotAllowedTheMessageSaysSoAndNothingElseHappens() {
        server.failOn("CREATE DATABASE `" + SCRATCH + "`", new SQLException("denied", "42000", 1044));
        assertEquals(I18n.t("backup.restore.maria.privileges", SCRATCH), refused().getMessage());
        assertFalse(server.ran("DELETE FROM"));
        assertFalse(server.ran("COMMIT"));
        assertTrue(server.ran("RELEASE_LOCK"));
    }

    @Test
    public void aDatabaseOfThatNameThatIsNotOursIsLeftAlone() {
        // five tables in it, and the marker table is not among them
        server = new FakeServer(SHOP).rule("GET_LOCK", null, row(1)).rule("RELEASE_LOCK", null, row(1))
                .rule("TABLE_SCHEMA = ? AND TABLE_NAME", SCRATCH, row(0))
                .rule("SELECT COUNT(*) FROM information_schema.TABLES", SCRATCH, row(5));
        assertEquals(I18n.t("backup.restore.maria.foreign", SCRATCH), refused().getMessage());
        assertFalse("not created and not emptied", server.ran("CREATE DATABASE") || server.ran("DROP DATABASE `"));
    }

    @Test
    public void aScratchDatabaseLeftByAnEarlierRunIsEmptiedAndUsedAgain() throws Exception {
        // an earlier run left tables behind, marker included
        FakeServer s = new FakeServer(SHOP).rule("GET_LOCK", null, row(1)).rule("RELEASE_LOCK", null, row(1))
                .rule("TABLE_SCHEMA = ? AND TABLE_NAME", SCRATCH, row(1))
                .rule("SELECT COUNT(*) FROM information_schema.TABLES", SCRATCH, row(4))
                .rule("SELECT TABLE_NAME FROM information_schema.TABLES", SHOP, row("users"), row("products"), row("orders"))
                .rule("FROM information_schema.COLUMNS", SCRATCH, SCRATCH_COLUMNS)
                .rule("FROM information_schema.COLUMNS", SHOP, LIVE_COLUMNS)
                .rule("SELECT version FROM schema_version", null, row(3))
                .rule("SELECT COUNT(*) FROM `", null, row(1))
                .rule("SELECT COUNT(*) FROM users WHERE role", null, row(1))
                .rule("KEY_COLUMN_USAGE", SCRATCH)
                .rule("SELECT MAX(order_date)", null, row("2026-10-30"));
        server = s;
        MariaDbRestore.check(server, dump);
        assertTrue(server.ran("DROP DATABASE IF EXISTS `" + SCRATCH + "`"));
        assertTrue(server.ran("CREATE DATABASE `" + SCRATCH + "`"));
    }

    @Test
    public void whenAnotherTillHoldsTheLockNothingHappens() {
        server = new FakeServer(SHOP).rule("GET_LOCK", null, row(0));
        assertEquals(I18n.t("backup.restore.maria.busy"), refused().getMessage());
        assertFalse(server.ran("DATABASE"));
        assertFalse(server.ran("HOOK"));
    }

    @Test
    public void aSafetyCopyThatFailsStopsTheRestoreBeforeAnyRowIsTouched() {
        hooks.failAfterCheck = true;
        refused();
        assertFalse(server.ran("DELETE FROM"));
        assertFalse(server.ran("COMMIT"));
        assertFalse(server.ran("HOOK beforeCommit"));
        assertTrue(server.ran("RELEASE_LOCK"));
        assertTrue(server.ran("DROP DATABASE IF EXISTS `" + SCRATCH + "`"));
    }

    // ---- failures inside the swap: all or nothing

    @Test
    public void aFailedCopyRollsEverythingBackAndNeverCommits() {
        server.failOn("FROM `" + SCRATCH + "`.`products`", new SQLException("bad row", "23000", 1062));
        assertEquals(I18n.t("backup.restore.maria.failed"), refused().getMessage());
        assertTrue(server.ran("ROLLBACK"));
        assertFalse(server.ran("COMMIT"));
        assertFalse("the pictures were not touched yet", server.ran("HOOK beforeCommit") || server.ran("HOOK undo"));
        assertTrue(server.ran("RELEASE_LOCK"));
        assertTrue(server.ran("DROP DATABASE IF EXISTS `" + SCRATCH + "`"));
    }

    @Test
    public void aRowCountThatDoesNotMatchRollsEverythingBack() {
        // after the products are copied the live table says 1 row while the backup said 2
        server = shortProductsServer();
        assertEquals(I18n.t("backup.restore.maria.counts", "products", 1L, 2L), refused().getMessage());
        assertTrue(server.ran("ROLLBACK"));
        assertFalse(server.ran("COMMIT"));
        assertFalse(server.ran("HOOK beforeCommit"));
    }

    /** The good server, except that once the products are copied the live table counts one row too few. The first rule that fits is the one used. */
    private FakeServer shortProductsServer() {
        FakeServer merged = new FakeServer(SHOP).ruleAfter("INSERT INTO `products` (", "SELECT COUNT(*) FROM `products`", null, row(1));
        return merged
                .rule("GET_LOCK", null, row(1)).rule("RELEASE_LOCK", null, row(1))
                .rule("SELECT COUNT(*) FROM information_schema.TABLES", SCRATCH, row(0))
                .rule("SELECT TABLE_NAME FROM information_schema.TABLES", SHOP,
                        row("users"), row("products"), row("orders"), row("schema_version"))
                .rule("FROM information_schema.COLUMNS", SCRATCH, SCRATCH_COLUMNS)
                .rule("FROM information_schema.COLUMNS", SHOP, LIVE_COLUMNS)
                .rule("SELECT version FROM schema_version", null, row(3))
                .rule("SELECT COUNT(*) FROM `orders`", null, row(1))
                .rule("SELECT COUNT(*) FROM `products`", null, row(2))
                .rule("SELECT COUNT(*) FROM `users`", null, row(1))
                .rule("SELECT COUNT(*) FROM users WHERE role", null, row(1))
                .rule("KEY_COLUMN_USAGE", SCRATCH)
                .rule("SELECT MAX(order_date)", null, row("2026-10-30 12:00:00"));
    }

    @Test
    public void aPictureStepThatFailsRollsBackAndAsksForTheUndo() {
        hooks.failBeforeCommit = true;
        refused();
        assertTrue(server.ran("ROLLBACK"));
        assertFalse(server.ran("COMMIT"));
        assertTrue(server.indexOf("HOOK beforeCommit") < server.indexOf("HOOK undo"));
        assertTrue(server.ran("RELEASE_LOCK"));
    }

    @Test
    public void aCommitThatFailsPutsTheRowsAndThePicturesBack() {
        server.commitFails = true;
        assertEquals(I18n.t("backup.restore.maria.failed"), refused().getMessage());
        assertTrue(server.ran("ROLLBACK"));
        assertTrue("the pictures were already swapped, so the undo is asked for", server.ran("HOOK undo"));
        assertTrue(server.ran("DROP DATABASE IF EXISTS `" + SCRATCH + "`"));
        assertTrue(server.ran("RELEASE_LOCK"));
    }

    @Test
    public void aLockWaitTimeoutIsExplainedAsAnotherTillBeingBusy() {
        server.failOn("DELETE FROM `orders`", new SQLException("Lock wait timeout exceeded", "HY000", 1205));
        assertEquals(I18n.t("backup.restore.maria.locked"), refused().getMessage());
        assertTrue(server.ran("ROLLBACK"));
        assertFalse(server.ran("COMMIT"));
    }

    @Test
    public void theScratchNameIsTheShopNameWithASuffix() {
        assertEquals("lunavera_restore_check", MariaDbRestore.scratchName("lunavera"));
    }
}
