package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * TODO 4.57: "Start selling for real". These tests need no database: they read the schemas' sources, so a table that only the
 * MariaDB schema has, or one added next month, is caught here and not on opening day.
 */
public class StartSellingRulesTest {

    private static final Path CONFIG = Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/config");
    private static final Pattern CREATE = Pattern.compile("CREATE TABLE IF NOT EXISTS\\s+([a-z_]+)");

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Set<String> tablesIn(String file) throws IOException {
        Matcher m = CREATE.matcher(read(CONFIG.resolve(file)));
        Set<String> names = new TreeSet<>();
        while (m.find()) names.add(m.group(1));
        return names;
    }

    @Test
    public void everyTableIsEitherKeptOrEmptiedOnPurpose() throws IOException {
        Set<String> schema = tablesIn("SqliteSchema.java");
        schema.addAll(tablesIn("MariaDbSchema.java"));
        assertTrue("the schemas were not read: " + schema, schema.size() > 20);
        Set<String> decided = new TreeSet<>(StartSellingRules.EMPTIED);
        decided.addAll(StartSellingRules.KEPT);
        decided.add(StartSellingRules.CUSTOMERS);
        Set<String> undecided = new TreeSet<>(schema);
        undecided.removeAll(decided);
        assertTrue("tables nobody decided about (add each to EMPTIED or KEPT in StartSellingRules): " + undecided, undecided.isEmpty());
        Set<String> unknown = new TreeSet<>(decided);
        unknown.removeAll(schema);
        // app_flags is created by code outside the two schema lists, so it is allowed to be absent from them
        unknown.remove("app_flags");
        assertTrue("tables in StartSellingRules that no schema creates: " + unknown, unknown.isEmpty());
    }

    @Test
    public void noTableIsInBothLists() {
        Set<String> seen = new TreeSet<>();
        List<String> twice = new ArrayList<>();
        for (String t : StartSellingRules.EMPTIED) if (!seen.add(t)) twice.add(t);
        for (String t : StartSellingRules.KEPT) if (!seen.add(t)) twice.add(t);
        if (!seen.add(StartSellingRules.CUSTOMERS)) twice.add(StartSellingRules.CUSTOMERS);
        assertTrue("listed twice: " + twice, twice.isEmpty());
    }

    @Test
    public void whatTheShopIsMadeOfIsNeverEmptied() {
        for (String kept : List.of("products", "categories", "users", "employees", "dining_tables", "ingredients", "recipes",
                "product_options", "promotions", "vouchers", "app_flags", "settings", "schema_version")) {
            assertFalse(kept + " must not be emptied", StartSellingRules.EMPTIED.contains(kept));
            assertTrue(kept + " must be listed as kept", StartSellingRules.KEPT.contains(kept));
        }
        for (String test : List.of("orders", "order_items", "order_payments", "refunds", "stock_log", "expenses", "shift_closes",
                "time_clock", "reservations", "gift_cards", "gift_card_moves", "audit_log", "stock_takes")) {
            assertTrue(test + " is test data and must be emptied", StartSellingRules.EMPTIED.contains(test));
        }
    }

    /** A row that points at another table must be deleted before it: the order of the list is the order of the DELETEs. */
    @Test
    public void aTableIsEmptiedBeforeTheTablesItPointsAt() throws IOException {
        String schema = read(CONFIG.resolve("SqliteSchema.java"));
        Pattern table = Pattern.compile("CREATE TABLE IF NOT EXISTS\\s+([a-z_]+)\\s*\\((.*?)\\n\\s*\\)\\n", Pattern.DOTALL);
        Pattern reference = Pattern.compile("REFERENCES\\s+([a-z_]+)\\(");
        Matcher m = table.matcher(schema);
        int checked = 0;
        while (m.find()) {
            String child = m.group(1);
            Matcher r = reference.matcher(m.group(2));
            while (r.find()) {
                String parent = r.group(1);
                int childAt = StartSellingRules.EMPTIED.indexOf(child);
                int parentAt = StartSellingRules.EMPTIED.indexOf(parent);
                if (childAt < 0 || parentAt < 0) continue;
                checked++;
                assertTrue(child + " points at " + parent + ", so it must come first in EMPTIED", childAt < parentAt);
            }
        }
        assertTrue("no foreign key was found: the pattern is wrong", checked >= 6);
    }

    @Test
    public void theShopNameIsAcceptedWhateverTheCapitalsAndSpaces() {
        assertTrue(StartSellingRules.nameMatches("Lunavera Coffee", "Lunavera Coffee"));
        assertTrue(StartSellingRules.nameMatches("  lunavera   COFFEE ", "Lunavera Coffee"));
        assertTrue(StartSellingRules.nameMatches("Quán Cà Phê Trăng", "quán cà phê trăng"));
    }

    @Test
    public void anotherTextIsRefused() {
        assertFalse(StartSellingRules.nameMatches("", "Lunavera Coffee"));
        assertFalse(StartSellingRules.nameMatches(null, "Lunavera Coffee"));
        assertFalse(StartSellingRules.nameMatches("Lunavera", "Lunavera Coffee"));
        assertFalse(StartSellingRules.nameMatches("Lunavera Coffee shop", "Lunavera Coffee"));
        assertFalse("a different accent is a different name", StartSellingRules.nameMatches("Quan Ca Phe", "Quán Cà Phê"));
        assertFalse("yes is not the name", StartSellingRules.nameMatches("yes", "Lunavera Coffee"));
    }

    @Test
    public void aShopWithoutANameIsConfirmedWithTheProgramsOwnName() {
        assertTrue(StartSellingRules.nameMatches("Lunavera Coffee", ""));
        assertTrue(StartSellingRules.nameMatches("lunavera coffee", null));
        assertFalse("an empty box must never confirm", StartSellingRules.nameMatches("", ""));
    }

    /** The order of the service is the safety of the action: the name, then the backup, then the wipe. Read from the source. */
    @Test
    public void theServiceChecksTheNameThenMakesTheBackupThenWipes() throws IOException {
        String source = read(Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/service/StartSellingService.java"));
        int start = source.indexOf("public static Result start(");
        assertTrue(start > 0);
        String body = source.substring(start);
        int admin = body.indexOf("Permissions.requireAdmin(");
        int name = body.indexOf("nameMatches(");
        int backup = body.indexOf("createBeforeOpeningBackup()");
        int connection = body.indexOf("DatabaseConfig.getConnection()");
        int wipe = body.indexOf("wipe(connection");
        int audit = body.indexOf("AuditDAO.write(");
        int commit = body.indexOf("connection.commit()");
        assertTrue("every step is there", admin > 0 && name > 0 && backup > 0 && connection > 0 && wipe > 0 && audit > 0 && commit > 0);
        assertTrue("administrator, then name, then backup, then the database",
                admin < name && name < backup && backup < connection);
        assertTrue("the audit line is written after the wipe and before the commit", wipe < audit && audit < commit);
        assertTrue("a failure rolls back", body.contains("connection.rollback()"));
    }

    @Test
    public void theBackupIsNotOptionalOnTheScreen() throws IOException {
        String dialog = read(Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/controller/StartSellingDialog.java"));
        assertFalse("the dialog must not offer a way to skip the backup", dialog.toLowerCase().contains("skipbackup"));
        assertTrue("Enter must not start the wipe: Cancel is the default button",
                dialog.contains("setDefaultButton(dialog, ButtonType.CANCEL)"));
        assertTrue("the start button stays off until the name is typed", dialog.contains("startButton.setDisable(true)"));
        assertTrue("the work runs off the screen thread", dialog.contains("Async.run(() -> StartSellingService.start("));
    }

    @Test
    public void theBeforeOpeningBackupIsNeverPruned() throws IOException {
        String source = read(Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/service/BackupService.java"));
        assertTrue(source.contains("BEFORE_OPENING_PREFIX"));
        int pruneCalls = 0;
        for (Matcher m = Pattern.compile("zipsWithPrefix\\(dir, ([A-Z_]+)\\)").matcher(source); m.find(); ) {
            assertNotEquals("the backup before opening is the only way back and must not be pruned", "BEFORE_OPENING_PREFIX", m.group(1));
            pruneCalls++;
        }
        assertTrue(pruneCalls >= 3);
    }
}
