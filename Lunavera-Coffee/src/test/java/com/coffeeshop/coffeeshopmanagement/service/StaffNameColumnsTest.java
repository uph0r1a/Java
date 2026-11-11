package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * TODO 2.87: the next table that copies a person's name cannot forget the person who is made anonymous. Reads the table definitions
 * and {@code AccountService} as text (no JavaFX, no database) and fails for any text column whose name ends in {@code _name} or
 * {@code _by} that is neither scrubbed when a staff member is made anonymous nor on the list of columns that hold somebody else's
 * name (a product, a guest). A new table therefore has to be put on one of the two lists, which is the moment to decide.
 */
public class StaffNameColumnsTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");

    /**
     * Columns that hold the name of the signed-in person, and the text that proves {@code AccountService.anonymiseStaff} rewrites
     * them (the audit log is rewritten by {@code AuditDAO.scrubActor}, called from there).
     */
    static final Map<String, String> SCRUBBED = Map.of(
            "employees.full_name", "UPDATE employees SET full_name",
            "stock_log.employee_name", "UPDATE stock_log SET employee_name",
            "shift_closes.closed_by_name", "UPDATE shift_closes SET closed_by_name",
            "refunds.refunded_by_name", "UPDATE refunds SET refunded_by_name",
            "expenses.recorded_by_name", "UPDATE expenses SET recorded_by_name",
            "time_clock.user_name", "UPDATE time_clock SET user_name",
            "gift_cards.sold_by", "UPDATE gift_cards SET sold_by",
            "reservations.created_by", "UPDATE reservations SET created_by",
            "audit_log.actor_name", "AuditDAO.scrubActor");

    /** Columns with a name or an "_by" that is not a member of staff: a product, a guest. */
    static final java.util.Set<String> NOT_STAFF = java.util.Set.of(
            "order_items.product_name", "stock_log.product_name", "reservations.customer_name",
            "customers.full_name"); // a customer: made anonymous by CustomerMergeService.anonymise, not here

    private static String read(String relative) throws IOException {
        return Files.readString(SOURCES.resolve(relative), StandardCharsets.UTF_8);
    }

    /** {@code table.column} of every TEXT / VARCHAR column that ends in _name or _by, from the SQLite table definitions. */
    static TreeSet<String> nameColumns(String schemaSource) {
        TreeSet<String> found = new TreeSet<>();
        Matcher table = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)\\s*\\((.*?)\\n\\s*\\)", Pattern.DOTALL).matcher(schemaSource);
        while (table.find()) {
            for (String line : table.group(2).split("\n")) {
                Matcher column = Pattern.compile("^\\s*(\\w+)\\s+(TEXT|VARCHAR)").matcher(line);
                if (column.find() && column.group(1).matches(".*(_name|_by)")) {
                    found.add(table.group(1) + "." + column.group(1));
                }
            }
        }
        return found;
    }

    @Test
    public void everyNameColumnIsEitherScrubbedOrKnownNotToBeStaff() throws IOException {
        List<String> unknown = new ArrayList<>();
        for (String column : nameColumns(read("config/SqliteSchema.java"))) {
            if (!SCRUBBED.containsKey(column) && !NOT_STAFF.contains(column)) unknown.add(column);
        }
        assertTrue("these columns hold a name but anonymising a staff member does not scrub them (add the UPDATE to "
                + "AccountService.scrubStaffNames, or list the column as not staff): " + unknown, unknown.isEmpty());
    }

    @Test
    public void everyScrubbedColumnReallyIsRewrittenByTheAnonymiseTransaction() throws IOException {
        String account = read("service/AccountService.java");
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(SCRUBBED).entrySet()) {
            if (!account.contains(entry.getValue())) missing.add(entry.getKey() + " (looked for: " + entry.getValue() + ")");
        }
        assertTrue("listed as scrubbed but AccountService has no such statement: " + missing, missing.isEmpty());
    }

    @Test
    public void bothListsOnlyNameColumnsThatExist() throws IOException {
        TreeSet<String> real = nameColumns(read("config/SqliteSchema.java"));
        List<String> stale = new ArrayList<>();
        for (String column : SCRUBBED.keySet()) if (!real.contains(column)) stale.add(column);
        for (String column : NOT_STAFF) if (!real.contains(column)) stale.add(column);
        assertTrue("on a list but not a column of any table any more: " + stale, stale.isEmpty());
    }

    @Test
    public void aNewTableWithANameColumnWouldBeCaught() {
        String schema = "CREATE TABLE IF NOT EXISTS invoices (\n    id INTEGER PRIMARY KEY,\n    issued_by TEXT NOT NULL,\n    total INTEGER\n)";
        TreeSet<String> found = nameColumns(schema);
        assertTrue(found.contains("invoices.issued_by"));
        assertTrue("and it is on neither list", !SCRUBBED.containsKey("invoices.issued_by") && !NOT_STAFF.contains("invoices.issued_by"));
    }

    @Test
    public void theTwoColumnsThatGotAnIdAreInBothSchemasAndTheImporter() throws IOException {
        String sqlite = read("config/SqliteSchema.java");
        String maria = read("config/MariaDbSchema.java");
        String importer = read("config/SqliteToMariaDbImporter.java");
        for (String column : new String[]{"sold_by_id", "created_by_id"}) {
            assertTrue(column + " must be added to an existing SQLite database", sqlite.contains("\"" + column + "\""));
            assertTrue(column + " must be in the MariaDB table", maria.contains(column + " INT NULL"));
            assertTrue(column + " must be copied by the importer", importer.contains("\"" + column + "\""));
        }
    }
}
