package com.coffeeshop.coffeeshopmanagement.dao;

import org.junit.Test;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.sql.SQLException;

import static org.junit.Assert.*;

/**
 * TODO 2.78: a failed write caused by a full disk tells the cashier so, instead of the generic "try again" that made them retry
 * a sale again and again. Needs no database: only exception objects.
 */
public class DiskFullHintTest {

    private static String hint(Throwable cause) {
        return DataAccessException.hintFor(cause);
    }

    @Test
    public void sqliteFullAndEveryIoErrorAreReportedAsAFullOrUnwritableDisk() {
        assertEquals(DataAccessException.diskFullHint(), hint(new SQLiteException("[SQLITE_FULL] The database is full", SQLiteErrorCode.SQLITE_FULL)));
        assertEquals(DataAccessException.diskFullHint(), hint(new SQLiteException("[SQLITE_IOERR] I/O error", SQLiteErrorCode.SQLITE_IOERR)));
        assertEquals("an extended code keeps the primary code in its low byte",
                DataAccessException.diskFullHint(), hint(new SQLiteException("[SQLITE_IOERR_SHMSIZE] I/O error within xShmSize", SQLiteErrorCode.SQLITE_IOERR_SHMSIZE)));
    }

    @Test
    public void mariaDbDiskAndTableFullErrorsAreReported() {
        assertEquals(DataAccessException.diskFullHint(), hint(new SQLException("Disk full (/tmp); waiting for someone to free some space", "HY000", 1021)));
        assertEquals(DataAccessException.diskFullHint(), hint(new SQLException("The table 'orders' is full", "HY000", 1114)));
        assertEquals(DataAccessException.diskFullHint(), hint(new SQLException("Got error 28 from storage engine", "HY000", 1030)));
    }

    @Test
    public void theOperatingSystemsOwnWordsAreAFallback() {
        assertEquals(DataAccessException.diskFullHint(), hint(new SQLException("write failed: No space left on device")));
        assertEquals(DataAccessException.diskFullHint(), hint(new SQLException("database or disk is full")));
        assertEquals(DataAccessException.diskFullHint(), hint(new SQLException("There is not enough space on the disk")));
    }

    @Test
    public void theHintIsFoundAnywhereInTheCauseChain() {
        Throwable wrapped = new RuntimeException("outer", new SQLiteException("[SQLITE_FULL] x", SQLiteErrorCode.SQLITE_FULL));
        assertEquals(DataAccessException.diskFullHint(), hint(wrapped));
    }

    @Test
    public void theMessageStartsWithTheHintAndKeepsTheOriginalText() {
        DataAccessException e = new DataAccessException("Failed to save order",
                new SQLiteException("[SQLITE_FULL] x", SQLiteErrorCode.SQLITE_FULL));
        assertTrue(e.getMessage(), e.getMessage().startsWith(DataAccessException.diskFullHint()));
        assertTrue(e.getMessage(), e.getMessage().endsWith("(Failed to save order)"));
    }

    @Test
    public void otherProblemsKeepTheirOwnHintsAndOrdinaryErrorsGetNone() {
        assertNotEquals(DataAccessException.diskFullHint(), hint(new SQLException("Access denied for user", "28000", 1045)));
        assertNotEquals(DataAccessException.diskFullHint(), hint(new SQLiteException("[SQLITE_BUSY] database is locked", SQLiteErrorCode.SQLITE_BUSY)));
        assertNull(hint(new SQLiteException("[SQLITE_CONSTRAINT_UNIQUE] x", SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE)));
        assertNull(hint(new SQLException("syntax error near x")));
        assertNull(hint(null));
    }
}
