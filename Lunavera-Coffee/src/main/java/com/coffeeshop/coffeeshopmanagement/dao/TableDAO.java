package com.coffeeshop.coffeeshopmanagement.dao;

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
            throw new DataAccessException("Không thể tải danh sách bàn: " + e.getMessage(), e);
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
            throw new DataAccessException("Không thể kiểm tra các bàn đang đánh dấu sử dụng: " + e.getMessage(), e);
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
            throw new DataAccessException("Không thể cập nhật trạng thái bàn " + tableNumber + ": " + e.getMessage(), e);
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
            throw new DataAccessException("Không thể giải phóng bàn " + tableNumber + ": " + e.getMessage(), e);
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
        return new DiningTable(id, tableNumber, name, status, capacity, currentOrderId);
    }
}
