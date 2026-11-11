package com.coffeeshop.coffeeshopmanagement.config;

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
 * TODO 3.18: the next text column cannot forget the composed-accent rule. Every TEXT column of the tables is either one the one-time
 * pass rewrites ({@code TextNormalization.COLUMNS}, whose values are also stored composed by the code that writes them) or is listed
 * here with the reason it holds no Vietnamese text a person typed. A new column fails this test until it is put on one side.
 * Reads the table definitions as text, no database.
 */
public class TextColumnsTest {

    private static final Path SCHEMA = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "config", "SqliteSchema.java");

    /** Columns that hold dates, fixed words, codes, numbers kept as text, hashes or text the program itself generated. */
    private static final Map<String, String> NOT_TYPED = new TreeMap<>(Map.ofEntries(
            Map.entry("order_items.extras", "list made by the program from the option names"), Map.entry("orders.channel", "fixed word"),
            Map.entry("product_options.kind", "fixed word"), Map.entry("promotions.kind", "fixed word"), Map.entry("promotions.days", "digits"),
            Map.entry("promotions.time_from", "time"), Map.entry("promotions.time_to", "time"),
            Map.entry("promotions.valid_from", "date"), Map.entry("promotions.valid_to", "date"),
            Map.entry("settings.name", "fixed key"), Map.entry("settings.value", "saved composed by ShopSettings.clean and the number/time parsers when written (TODO 6.74)"),
            Map.entry("employees.phone", "digits"), Map.entry("employees.email", "ASCII address"), Map.entry("employees.hire_date", "date"),
            Map.entry("customers.phone", "digits"), Map.entry("customers.email", "ASCII address"),
            Map.entry("users.username", "login name: its own case-insensitive rule"),
            Map.entry("products.image_path", "file path made by the program"),
            Map.entry("orders.payment_method", "fixed word"), Map.entry("orders.complimentary_kind", "fixed word (STAFF, OWNER, TASTING)"), Map.entry("orders.transfer_ref", "bank reference, ASCII"),
            Map.entry("orders.voucher_code", "code"), Map.entry("users.pin_hash", "hash"), Map.entry("products.code", "code"),
            Map.entry("login_attempts.scope", "fixed word"), Map.entry("login_attempts.name_key", "lower-cased login name, expires"),
            Map.entry("stock_log.logged_at", "date"), Map.entry("stock_log.kind", "fixed word"),
            Map.entry("audit_log.logged_at", "date"), Map.entry("audit_log.actor_name", "history is not rewritten (anonymised separately)"),
            Map.entry("audit_log.action", "fixed word"), Map.entry("audit_log.target_type", "fixed word"),
            Map.entry("audit_log.target_label", "history is not rewritten"), Map.entry("audit_log.old_value", "history is not rewritten"),
            Map.entry("audit_log.new_value", "history is not rewritten"), Map.entry("audit_log.detail", "history is not rewritten"),
            Map.entry("expenses.category", "fixed word"), Map.entry("gift_cards.code", "code"), Map.entry("gift_card_moves.moved_at", "date"),
            Map.entry("reservations.phone", "digits"), Map.entry("vouchers.code", "code"), Map.entry("vouchers.kind", "fixed word"),
            Map.entry("vouchers.valid_from", "date"), Map.entry("vouchers.valid_to", "date"),
            Map.entry("order_payments.method", "fixed word"), Map.entry("refunds.method", "fixed word"),
            Map.entry("time_clock.clock_in", "date"), Map.entry("time_clock.clock_out", "date"),
            Map.entry("shift_closes.period_start", "date"), Map.entry("shift_closes.period_end", "date")));

    /** Names that are dates, fixed words or hashes by their ending; the explicit list above is for the rest. */
    private static final Pattern PLAIN = Pattern.compile(".*(_at|_on|_for|_date|status|role|_hash|password_hash|created_at)$");

    static TreeSet<String> textColumns(String schema) {
        TreeSet<String> found = new TreeSet<>();
        Matcher table = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)\\s*\\((.*?)\\n\\s*\\)", Pattern.DOTALL).matcher(schema);
        while (table.find()) {
            for (String line : table.group(2).split("\n")) {
                Matcher column = Pattern.compile("^\\s*(\\w+)\\s+(TEXT|VARCHAR)").matcher(line);
                if (column.find()) found.add(table.group(1) + "." + column.group(1));
            }
        }
        return found;
    }

    /** The TEXT columns, in the tables and in the later-columns list, that are neither rewritten nor explained. */
    static List<String> unclassified(String schema, java.util.Collection<String> rewritten) {
        TreeSet<String> all = textColumns(schema);
        Matcher later = Pattern.compile("new String\\[\\]\\{\"(\\w+)\", \"(\\w+)\", \"(?:TEXT|VARCHAR[^\"]*)\"\\}").matcher(schema);
        while (later.find()) all.add(later.group(1) + "." + later.group(2));
        List<String> missing = new ArrayList<>();
        for (String column : all) {
            String name = column.substring(column.indexOf('.') + 1);
            if (rewritten.contains(column) || NOT_TYPED.containsKey(column) || PLAIN.matcher(name).matches()) continue;
            missing.add(column);
        }
        return missing;
    }

    @Test
    public void everyTextColumnIsRewrittenOrKnownNotToHoldTypedText() throws IOException {
        List<String> missing = unclassified(Files.readString(SCHEMA, StandardCharsets.UTF_8), TextNormalization.COLUMNS);
        assertTrue("these TEXT columns are neither in TextNormalization.COLUMNS nor explained in TextColumnsTest.NOT_TYPED "
                + "(a name or a note a person types must be stored composed and rewritten for old rows): " + missing, missing.isEmpty());
    }

    @Test
    public void everyRewrittenColumnReallyExists() throws IOException {
        TreeSet<String> real = textColumns(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        Matcher later = Pattern.compile("new String\\[\\]\\{\"(\\w+)\", \"(\\w+)\", \"(?:TEXT|VARCHAR[^\"]*)\"\\}")
                .matcher(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        while (later.find()) real.add(later.group(1) + "." + later.group(2));
        List<String> unknown = new ArrayList<>();
        for (String column : TextNormalization.COLUMNS) if (!real.contains(column)) unknown.add(column);
        assertTrue("listed for rewriting but not a text column of any table: " + unknown, unknown.isEmpty());
    }

    @Test
    public void aNewTextColumnWouldBeCaught() {
        String schema = "CREATE TABLE IF NOT EXISTS menus (\n    id INTEGER PRIMARY KEY,\n    headline TEXT NOT NULL,\n    shown_at TEXT\n)";
        assertTrue(unclassified(schema, List.of()).equals(List.of("menus.headline")));
    }
}
