package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.service.TableRules;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class TableDAO {

    public List<DiningTable> findAll() {
        String sql = """
            SELECT
                d.id,
                d.table_number,
                d.name,
                d.capacity,
                o.id AS open_order_id,
                oo.order_date AS opened_at,
                CASE
                    WHEN o.id IS NOT NULL OR d.status = %s THEN %s
                    ELSE %s
                END AS computed_status
            FROM dining_tables d
            LEFT JOIN (
                SELECT MAX(id) AS id, table_number
                FROM orders
                WHERE status = %s AND table_number IS NOT NULL
                GROUP BY table_number
            ) o ON o.table_number = d.table_number
            LEFT JOIN orders oo ON oo.id = o.id
            ORDER BY d.table_number ASC
        """.formatted(SqlStatus.OCCUPIED, SqlStatus.OCCUPIED, SqlStatus.EMPTY, SqlStatus.OPEN);
        List<DiningTable> tables = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                tables.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("pos.load.tables", e.getMessage()), e);
        }
        return tables;
    }

    /**
     * Tables that were switched to "Đang sử dụng" by hand and have no OPEN order (TODO 2.53). The table state has one source of
     * truth for orders: a table with an OPEN order is occupied because of that order, and nothing else is written when an order
     * is started. The stored status is only the hand-made "in use" mark; it is cleared in the same transaction as a payment or
     * a cancel, and a mark left behind is listed here so the dashboard can flag it.
     */
    public List<Integer> holdsWithoutOrder() {
        String sql = "SELECT d.table_number FROM dining_tables d WHERE d.status = " + SqlStatus.OCCUPIED + " AND NOT EXISTS "
                + "(SELECT 1 FROM orders o WHERE o.table_number = d.table_number AND o.status = " + SqlStatus.OPEN + ") ORDER BY d.table_number";
        List<Integer> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) result.add(rs.getInt(1));
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("table.check.fail", e.getMessage()), e);
        }
        return result;
    }

    /** Only for the hand-made mark ({@link #setOccupied}) and for freeing a table; an order never needs it. */
    public void updateStatus(int tableNumber, TableStatus status, Integer orderId) {
        String sql = "UPDATE dining_tables SET status = ?, current_order_id = ? WHERE table_number = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            if (orderId != null) {
                statement.setInt(2, orderId);
            } else {
                statement.setNull(2, Types.INTEGER);
            }
            statement.setInt(3, tableNumber);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("table.update.fail", tableNumber, e.getMessage()), e);
        }
    }

    public void setOccupied(int tableNumber, Integer orderId) {
        updateStatus(tableNumber, TableStatus.OCCUPIED, orderId);
    }

    /** Frees the table and cancels its OPEN order, both or neither (one transaction). */
    public void setEmpty(int tableNumber) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE dining_tables SET status = " + SqlStatus.EMPTY + ", current_order_id = NULL WHERE table_number = ?")) {
                statement.setInt(1, tableNumber);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE orders SET status = " + SqlStatus.CANCELLED + " WHERE table_number = ? AND status = " + SqlStatus.OPEN)) {
                statement.setInt(1, tableNumber);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("table.release.fail", tableNumber, e.getMessage()), e);
        }
    }

    // ============================================================ Table management (TODO 4.18)

    /**
     * Adds a table and returns its number. The number is one more than the highest number any table or any order ever used, so
     * a removed table's number is never given to a new table (old orders keep the number and would show up on it).
     * A blank name becomes "Bàn N", the starter style, which follows the language on screen. Administrator only.
     *
     * <p>Two tills adding at the same moment can pick the same number; the table number is unique, so the second insert is
     * refused by the database. That is not an error for the owner: the number is chosen again, up to three times.
     *
     * @throws IllegalArgumentException with the reason in plain words: too many tables, a name or seats that are not allowed
     */
    public int add(String name, int capacity) {
        return add(name, capacity, () -> { });
    }

    /** For tests: {@code afterNumberChosen} runs between choosing the number and inserting, where a second till could act. */
    int add(String name, int capacity, Runnable afterNumberChosen) {
        Permissions.requireAdmin("add a table");
        for (int attempt = 1; ; attempt++) {
            try (Connection connection = DatabaseConfig.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    List<DiningTable> existing = tablesOf(connection);
                    if (existing.size() >= TableRules.MAX_TABLES) {
                        throw new IllegalArgumentException(I18n.t("tablemgr.val.max", TableRules.MAX_TABLES));
                    }
                    int highestOrder = highest(connection, "SELECT MAX(table_number) FROM orders");
                    int highestTable = existing.stream().mapToInt(DiningTable::getTableNumber).max().orElse(0);
                    int number = TableRules.nextNumber(highestTable, highestOrder);
                    String clean = name == null ? "" : TextNormalizer.nfc(name);
                    if (clean.isEmpty()) {
                        clean = "B\u00e0n " + number;
                    }
                    checkNameAndSeats(clean, capacity, existing, -1);
                    afterNumberChosen.run();
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO dining_tables (table_number, name, status, capacity) VALUES (?, ?, " + SqlStatus.EMPTY + ", ?)")) {
                        insert.setInt(1, number);
                        insert.setString(2, clean);
                        insert.setInt(3, capacity);
                        insert.executeUpdate();
                    }
                    connection.commit();
                    return number;
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                if (attempt < 3 && (UniqueViolation.isUnique(e) || isBusy(e))) {
                    continue; // another till took this number a moment ago: pick the next one
                }
                if (UniqueViolation.isUnique(e) || isBusy(e)) {
                    throw new DataAccessException(I18n.t("tablemgr.fail.busy"), e);
                }
                throw new DataAccessException(I18n.t("tablemgr.fail.add", e.getMessage()), e);
            }
        }
    }

    /**
     * Renames a table and sets its seats. The number never changes: orders point at it. Administrator only.
     *
     * @throws IllegalArgumentException when the name or the seats are not allowed, or the table is gone
     */
    public void update(int tableNumber, String name, int capacity) {
        Permissions.requireAdmin("change a table");
        String clean = name == null ? "" : TextNormalizer.nfc(name);
        try (Connection connection = DatabaseConfig.getConnection()) {
            List<DiningTable> existing = tablesOf(connection);
            checkNameAndSeats(clean, capacity, existing, tableNumber);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE dining_tables SET name = ?, capacity = ? WHERE table_number = ?")) {
                statement.setString(1, clean);
                statement.setInt(2, capacity);
                statement.setInt(3, tableNumber);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException(I18n.t("tablemgr.gone"));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("tablemgr.fail.update", e.getMessage()), e);
        }
    }

    /**
     * Removes a table that nobody is using. One statement decides, so an order opened on the table a moment earlier on another
     * till cannot be orphaned: the row goes only if the table has no OPEN order and no hand-made "in use" mark, and only if it
     * is not the last table (the count is read through a derived table, which MariaDB needs to read the table it deletes from).
     * Administrator only.
     *
     * @return true when the table is gone (or was already gone); false when it is in use and was kept
     * @throws IllegalArgumentException when it is the last table
     */
    public boolean remove(int tableNumber) {
        Permissions.requireAdmin("remove a table");
        String delete = "DELETE FROM dining_tables WHERE table_number = ? AND status <> " + SqlStatus.OCCUPIED
                + " AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.table_number = ? AND o.status = " + SqlStatus.OPEN + ")"
                + " AND (SELECT COUNT(*) FROM (SELECT table_number FROM dining_tables) all_tables) > 1";
        try (Connection connection = DatabaseConfig.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setInt(1, tableNumber);
                statement.setInt(2, tableNumber);
                if (statement.executeUpdate() > 0) {
                    return true;
                }
            }
            List<DiningTable> left = tablesOf(connection);
            if (left.stream().noneMatch(t -> t.getTableNumber() == tableNumber)) {
                return true; // already gone
            }
            if (left.size() <= 1) {
                throw new IllegalArgumentException(I18n.t("tablemgr.remove.last"));
            }
            return false; // in use
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("tablemgr.fail.remove", e.getMessage()), e);
        }
    }

    /** The tables with their stored names, read on the given connection (the names are all the rules need). */
    private static List<DiningTable> tablesOf(Connection connection) throws SQLException {
        List<DiningTable> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_number, name, capacity FROM dining_tables ORDER BY table_number");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                tables.add(new DiningTable(0, rs.getInt(1), rs.getString(2), TableStatus.EMPTY, rs.getInt(3), null));
            }
        }
        return tables;
    }

    /** The same rules as the edit box, again here, because the box is not the only way in (and a second till may have changed things). */
    private static void checkNameAndSeats(String name, int capacity, List<DiningTable> existing, int exceptNumber) {
        List<String> others = new ArrayList<>();
        for (DiningTable t : existing) {
            if (t.getTableNumber() != exceptNumber) {
                others.addAll(TableRules.namesOf(t.getTableNumber(), t.getName()));
            }
        }
        String problem = TableRules.nameProblem(name, others);
        if (problem == null) {
            problem = TableRules.capacityProblem(String.valueOf(capacity));
        }
        if (problem != null) {
            throw new IllegalArgumentException(TableRules.describe(problem));
        }
    }

    /** SQLite's "database is locked" family: a till that wrote first made this till's read of the number out of date. */
    private static boolean isBusy(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        return message.contains("SQLITE_BUSY") || message.contains("database is locked") || e.getErrorCode() == 5 || e.getErrorCode() == 517;
    }

    private static int highest(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0; // MAX of nothing is NULL, which getInt reads as 0
        }
    }

    private DiningTable mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int tableNumber = rs.getInt("table_number");
        String name = rs.getString("name");
        TableStatus status = TableStatus.fromDb(rs.getString("computed_status"));
        int capacity = rs.getInt("capacity");
        int orderId = rs.getInt("open_order_id");
        Integer currentOrderId = rs.wasNull() ? null : orderId;
        DiningTable table = new DiningTable(id, tableNumber, name, status, capacity, currentOrderId);
        table.setOpenedAt(readOpenedAt(rs.getString("opened_at")));
        return table;
    }

    /** The time the open order was started (TODO 5.11). A damaged value only means "no age shown", never a failed table map. */
    private static java.time.LocalDateTime readOpenedAt(String text) {
        try {
            return com.coffeeshop.coffeeshopmanagement.util.DbTime.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
