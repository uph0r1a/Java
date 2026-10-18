package com.coffeeshop.coffeeshopmanagement.dao;

import org.junit.Test;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A problem the person at the till can act on is explained in plain Vietnamese; everything else is left alone. */
public class DataAccessExceptionTest {

    private static String message(Throwable cause) {
        return new DataAccessException("Failed to load orders", cause).getMessage();
    }

    @Test
    public void aServerThatCannotBeReachedIsExplained() {
        for (SQLException cause : new SQLException[]{
                new SQLNonTransientConnectionException("Could not connect to address=(host=127.0.0.1)(port=3306)", "08000"),
                new SQLException("Communications link failure", "HY000"),
                new SQLException("Connection refused: connect")}) {
            String text = message(cause);
            assertTrue(text, text.startsWith("Không kết nối được máy chủ cơ sở dữ liệu"));
            assertTrue("the technical message is kept", text.endsWith("(Failed to load orders)"));
        }
    }

    @Test
    public void aWrongLoginIsExplainedAndNamesTheSettingsFile() {
        String text = message(new SQLException("Access denied for user 'lunavera'@'localhost'", "28000", 1045));
        assertTrue(text, text.startsWith("Sai tài khoản hoặc mật khẩu"));
        assertTrue(text, text.contains("data/database.properties"));
    }

    @Test
    public void aBusyDatabaseIsExplainedForBothDatabases() {
        assertTrue(message(new SQLException("[SQLITE_BUSY] The database file is locked (database is locked)"))
                .startsWith("Cơ sở dữ liệu đang bận"));
        assertTrue(message(new SQLException("Lock wait timeout exceeded; try restarting transaction", "HY000", 1205))
                .startsWith("Cơ sở dữ liệu đang bận"));
        assertTrue(message(new SQLException("Deadlock found when trying to get lock", "40001", 1213))
                .startsWith("Cơ sở dữ liệu đang bận"));
    }

    @Test
    public void theCauseIsFoundEvenWhenItIsWrappedAgain() {
        Throwable wrapped = new RuntimeException("outer", new SQLException("Communications link failure"));
        assertTrue(message(wrapped).startsWith("Không kết nối được máy chủ"));
    }

    @Test
    public void everyOtherFailureKeepsItsMessageExactly() {
        assertEquals("Failed to load orders", message(new SQLException("UNIQUE constraint failed: customers.phone")));
        assertEquals("Failed to load orders", message(new RuntimeException("something else")));
        assertEquals("Failed to load orders", message(null));
        assertEquals("No cause given", new DataAccessException("No cause given").getMessage());
    }
}
