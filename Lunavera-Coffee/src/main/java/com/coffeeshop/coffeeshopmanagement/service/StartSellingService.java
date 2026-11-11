package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Start selling for real" (TODO 4.57): clears what the owner produced while trying the till, and keeps the shop itself.
 *
 * <p><b>Order of things.</b> (1) the administrator check; (2) the typed shop name must match; (3) a full backup is made, and if it
 * cannot be made nothing is touched; (4) one transaction empties the tables of {@link StartSellingRules#EMPTIED}, frees the
 * tables, sets the vouchers' use count and the customers' points back to nothing (or removes the customers when asked), and
 * writes ONE audit line, after the wipe, saying who did it; (5) commit. A failure in step 4 rolls everything back.
 *
 * <p>The menu, the tables, the staff and their accounts, the settings, the ingredients and recipes, the promotions and the vouchers
 * themselves stay. The numbers kept in stock are not changed (the sales that took them down are gone, but a stock count is the
 * owner's to set), and the order numbers go on from where they were.
 */
public final class StartSellingService {

    /** What was done: the backup that was made and how many rows left each emptied table. */
    public record Result(String backupName, Map<String, Integer> removed, boolean customersRemoved) {
        public int total() {
            return removed.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private StartSellingService() {
    }

    /** How many rows each emptied table holds now (and the customers' table, when asked), for the confirmation text. */
    public static Map<String, Integer> preview(boolean withCustomers) {
        Permissions.requireAdmin("read what clearing the test data would remove");
        try (Connection connection = DatabaseConfig.getConnection()) {
            return counts(connection, withCustomers);
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("startselling.fail"), e);
        }
    }

    /**
     * Makes the backup, then clears the test data in one transaction.
     *
     * @throws DataAccessException with a sentence fit to show; when it is thrown after the backup, the data is untouched
     */
    public static Result start(String typedName, boolean withCustomers) {
        Permissions.requireAdmin("clear the test data");
        String shopName = StartSellingRules.expectedName();
        if (!StartSellingRules.nameMatches(typedName, shopName)) {
            throw new DataAccessException(I18n.t("startselling.name.wrong"));
        }
        final Path backup;
        try {
            backup = BackupService.createBeforeOpeningBackup();
        } catch (SQLException | IOException | RuntimeException e) {
            throw new DataAccessException(I18n.t("startselling.backup.fail", String.valueOf(e.getMessage())), e);
        }
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Integer> removed = wipe(connection, withCustomers);
                int orders = removed.getOrDefault("orders", 0);
                int others = removed.values().stream().mapToInt(Integer::intValue).sum() - orders;
                // After the wipe, so the line is the first one in the new log (the old lines went with the test data).
                AuditDAO.write(connection, AuditAction.SALES_STARTED, AuditTarget.SHOP, null, shopName, null,
                        backup.getFileName().toString(), orders + "|" + others + "|" + (withCustomers ? 1 : 0));
                connection.commit();
                SecurityLog.event(SecurityLog.actor() + " cleared the test data and started selling for real (" + orders
                        + " orders, " + others + " other rows removed" + (withCustomers ? ", customers too" : "")
                        + "; backup " + backup.getFileName() + ")");
                return new Result(backup.getFileName().toString(), removed, withCustomers);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("startselling.fail"), e);
        }
    }

    // ------------------------------------------------------------------ the statements

    private static Map<String, Integer> counts(Connection connection, boolean withCustomers) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : StartSellingRules.EMPTIED) counts.put(table, count(connection, table));
        if (withCustomers) counts.put(StartSellingRules.CUSTOMERS, count(connection, StartSellingRules.CUSTOMERS));
        return counts;
    }

    private static int count(Connection connection, String table) throws SQLException {
        // The table names come from the constants of StartSellingRules, never from the screen.
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Runs on the caller's transaction; returns how many rows each emptied table held. */
    private static Map<String, Integer> wipe(Connection connection, boolean withCustomers) throws SQLException {
        Map<String, Integer> removed = counts(connection, withCustomers);
        // A table points at its open order: free every table first, or the orders could not be deleted.
        update(connection, "UPDATE dining_tables SET status = " + SqlStatus.EMPTY + ", current_order_id = NULL");
        for (String table : StartSellingRules.EMPTIED) {
            update(connection, "DELETE FROM " + table);
        }
        update(connection, "UPDATE vouchers SET used_count = 0");
        if (withCustomers) {
            update(connection, "UPDATE users SET customer_id = NULL WHERE customer_id IS NOT NULL"); // an old customer login stays, unlinked
            update(connection, "DELETE FROM " + StartSellingRules.CUSTOMERS);
        } else {
            update(connection, "UPDATE customers SET loyalty_points = 0, lifetime_points = 0, birthday_used_on = NULL");
        }
        return removed;
    }

    private static void update(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
