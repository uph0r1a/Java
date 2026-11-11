package com.coffeeshop.coffeeshopmanagement.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Brings the Vietnamese text that was saved before TODO 3.18 into the composed form (NFC), once. Since that item every name and note
 * is stored composed, but a database that is older keeps whatever was typed ("a" plus a separate accent mark is a different text
 * from "à"), so searches, uniqueness checks and reports saw two words where the shop sees one. The text is changed only in its
 * accent encoding: no space is touched, no case, nothing is trimmed.
 *
 * <p>A name that would become the same as another row's name in a column that must be unique is left as it was and logged, never
 * merged or failed on: the owner then sees two names that look alike and decides. It runs once ({@link #FLAG}) after the schema and
 * the import, in one transaction, and a database with nothing to change just sets the flag.
 */
final class TextNormalization {

    private static final Logger LOGGER = Logger.getLogger(TextNormalization.class.getName());

    /** Set in {@code app_flags} when the one-time pass has run. */
    static final String FLAG = "text_nfc_done";

    /**
     * Every column that holds text a person typed or a name copied from it, as {@code table.column}; each table has an {@code id}.
     * Not here: logins ({@code users.username} has its own rule), codes and references, the audit log (history is not rewritten) and
     * the generated texts; {@code TextColumnsTest} makes the next text column choose a side.
     */
    static final List<String> COLUMNS = List.of(
            "categories.name", "categories.description", "products.name", "products.description", "customers.full_name",
            "employees.full_name", "employees.address", "employees.position", "dining_tables.name", "ingredients.name", "ingredients.unit",
            "order_items.product_name", "order_items.note", "orders.discount_reason", "orders.cancel_reason",
            "stock_log.product_name", "stock_log.supplier", "stock_log.note", "stock_log.employee_name",
            "refunds.reason", "refunds.refunded_by_name", "expenses.note", "expenses.recorded_by_name",
            "reservations.customer_name", "reservations.note", "reservations.created_by",
            "shift_closes.note", "shift_closes.closed_by_name", "time_clock.user_name", "gift_cards.sold_by",
            "promotions.name", "product_options.name", "order_items.promo_name", "order_items.size_name", "orders.complimentary_note");

    /** Columns where the database refuses two equal values, so a change must not collide with another row. */
    static final Set<String> UNIQUE = Set.of("categories.name", "ingredients.name");

    private TextNormalization() {
    }

    /** One text of a column: the row's id and what is saved. */
    record Row(int id, String text) {
    }

    /** A row whose text is rewritten. */
    record Change(int id, String to) {
    }

    /** What to do with one column: the rewrites, and the rows left alone because the composed text is already another row's. */
    record Plan(List<Change> changes, List<Integer> collisions) {
    }

    /** The composed form of a text; null stays null. Nothing else about the text changes. */
    static String composed(String text) {
        return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    /**
     * The rewrites for the rows of one column. {@code unique}: a composed text that is (or becomes) another row's text is not
     * written, and that row's id is reported instead; the first row to claim a text keeps it.
     */
    static Plan plan(List<Row> rows, boolean unique) {
        Map<String, Integer> owner = new HashMap<>();
        if (unique) {
            for (Row row : rows) {
                if (row.text() != null) owner.putIfAbsent(row.text(), row.id());
            }
        }
        List<Change> changes = new ArrayList<>();
        List<Integer> collisions = new ArrayList<>();
        for (Row row : rows) {
            String now = composed(row.text());
            if (now == null || now.equals(row.text())) continue;
            if (unique) {
                Integer other = owner.get(now);
                if (other != null && other != row.id()) {
                    collisions.add(row.id());
                    continue;
                }
                owner.put(now, row.id());
            }
            changes.add(new Change(row.id(), now));
        }
        return new Plan(changes, collisions);
    }

    /** What a run did, for the log and the tests. */
    record Result(int rewritten, List<String> collisions) {
    }

    /** Runs the pass once: nothing happens when {@link #FLAG} is already set. */
    static Result runOnce(Connection connection) throws SQLException {
        DatabaseConfig.ensureFlagsTable(connection);
        try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM app_flags WHERE name = ?")) {
            check.setString(1, FLAG);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return new Result(0, List.of());
            }
        }
        Result[] result = new Result[1];
        DatabaseConfig.runAtomically(connection, () -> {
            result[0] = run(connection);
            try (PreparedStatement flag = connection.prepareStatement(SqlDialect.of(connection).insertIgnoreInto("app_flags") + " (name) VALUES (?)")) {
                flag.setString(1, FLAG);
                flag.executeUpdate();
            }
        });
        if (result[0].rewritten() > 0 || !result[0].collisions().isEmpty()) {
            LOGGER.log(Level.INFO, "Text normalised to the composed form: {0} rows rewritten, left alone because the name already exists: {1}",
                    new Object[]{result[0].rewritten(), result[0].collisions()});
        }
        return result[0];
    }

    /** The pass itself, without the flag (the tests call it on a database that already ran once). */
    static Result run(Connection connection) throws SQLException {
        int rewritten = 0;
        List<String> collisions = new ArrayList<>();
        for (String column : COLUMNS) {
            String table = column.substring(0, column.indexOf('.'));
            String name = column.substring(column.indexOf('.') + 1);
            List<Row> rows = new ArrayList<>();
            try (Statement select = connection.createStatement();
                 ResultSet rs = select.executeQuery("SELECT id, " + name + " FROM " + table + " WHERE " + name + " IS NOT NULL")) {
                while (rs.next()) rows.add(new Row(rs.getInt(1), rs.getString(2)));
            }
            Plan plan = plan(rows, UNIQUE.contains(column));
            for (int id : plan.collisions()) collisions.add(column + "#" + id);
            if (plan.changes().isEmpty()) continue;
            try (PreparedStatement update = connection.prepareStatement("UPDATE " + table + " SET " + name + " = ? WHERE id = ?")) {
                for (Change change : plan.changes()) {
                    update.setString(1, change.to());
                    update.setInt(2, change.id());
                    update.addBatch();
                }
                update.executeBatch();
            }
            rewritten += plan.changes().size();
        }
        return new Result(rewritten, collisions);
    }
}
