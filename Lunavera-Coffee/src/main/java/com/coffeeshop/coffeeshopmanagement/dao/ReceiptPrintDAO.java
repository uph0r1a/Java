package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.util.OrderNumber;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The record of printed receipts (TODO 3.72). A receipt printed a second time must not pass for the first: the printed page says
 * "COPY - print no. N" ({@code ReceiptText.copyMark}), and here the print is counted in {@code orders.print_count} and, from the second
 * on, written to the audit log with who printed it. Both in one transaction, so a count never goes up without its line.
 */
public final class ReceiptPrintDAO {

    /**
     * Notes that the receipt of the order was printed. The count goes up by one in a single statement (two tills printing at the same
     * moment get two different numbers); a reprint (the count is now 2 or more) also writes {@link AuditAction#RECEIPT_REPRINTED}.
     *
     * @return the count after this print (1 = the first print), or 0 when there is no such order
     */
    public int recordPrint(int orderId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int count;
                try (PreparedStatement update = connection.prepareStatement("UPDATE orders SET print_count = print_count + 1 WHERE id = ?")) {
                    update.setInt(1, orderId);
                    if (update.executeUpdate() == 0) {
                        connection.rollback();
                        return 0;
                    }
                }
                try (PreparedStatement read = connection.prepareStatement("SELECT print_count FROM orders WHERE id = ?")) {
                    read.setInt(1, orderId);
                    try (ResultSet rs = read.executeQuery()) {
                        rs.next();
                        count = rs.getInt(1);
                    }
                }
                if (count >= 2) {
                    AuditDAO.write(connection, AuditAction.RECEIPT_REPRINTED, AuditTarget.ORDER, orderId, OrderNumber.format(orderId),
                            String.valueOf(count - 1), String.valueOf(count), null);
                }
                connection.commit();
                return count;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record the printed receipt", e);
        }
    }
}
