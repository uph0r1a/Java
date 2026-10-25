package com.coffeeshop.coffeeshopmanagement.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A named lock held on the MariaDB/MySQL server ({@code GET_LOCK}), so that several tills coordinate through the one thing they
 * share (TODO 3.50, 3.51). The lock belongs to the <em>connection</em> that took it: it is released by {@link #release}, or by
 * the server when that connection ends, so a till that is closed or crashes never leaves one behind.
 */
final class ServerLock {

    private ServerLock() {
    }

    /**
     * Takes the lock, waiting up to {@code timeoutSeconds} for another holder to let go (0 = do not wait).
     *
     * @return true when this connection now holds the lock; false when someone else holds it, or the server could not say
     */
    static boolean acquire(Connection connection, String name, int timeoutSeconds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, name);
            statement.setInt(2, timeoutSeconds);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return false;
                Object answer = rs.getObject(1); // 1 = got it, 0 = timed out, NULL = error
                return answer instanceof Number number && number.intValue() == 1;
            }
        }
    }

    /** Lets go of a lock this connection took; a problem is ignored, because ending the connection releases it anyway. */
    static void release(Connection connection, String name) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, name);
            statement.executeQuery().close();
        } catch (SQLException | RuntimeException ignored) {
            // the lock goes away with the connection
        }
    }
}
