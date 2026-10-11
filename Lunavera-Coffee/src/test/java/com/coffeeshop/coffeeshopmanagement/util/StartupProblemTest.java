package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 2.5: the message shown when the database cannot be opened. */
public class StartupProblemTest {

    @Test
    public void theMessageNamesTheFoldersAndTheRealCause() {
        Throwable error = new IllegalStateException("Could not initialize the database",
                new java.sql.SQLException("[SQLITE_BUSY] The database file is locked"));
        String text = StartupProblem.describe(error, "D:\\Lunavera\\data", "D:\\Lunavera\\data\\logs", "D:\\Lunavera\\data\\backups");
        assertTrue(text.contains("D:\\Lunavera\\data"));
        assertTrue(text.contains("D:\\Lunavera\\data\\logs"));
        assertTrue(text.contains("D:\\Lunavera\\data\\backups"));
        assertTrue("the deepest cause is shown", text.contains("SQLITE_BUSY"));
        assertTrue("says the data is not deleted", text.contains("chưa bị xóa"));
        assertFalse("not the wrapper's English message", text.contains("Could not initialize the database"));
    }

    @Test
    public void aCauseWithoutAMessageShowsItsClassName() {
        assertEquals("NullPointerException", StartupProblem.rootMessage(new RuntimeException(new NullPointerException())));
        assertEquals("(không rõ)", StartupProblem.rootMessage(null));
    }

    @Test
    public void aCauseLoopDoesNotHangTheMessage() {
        Exception a = new Exception("a");
        Exception b = new Exception("b", a);
        a.initCause(b);
        assertNotNull(StartupProblem.describe(b, "d", "l", "b"));
    }

    @Test
    public void describeWorksWithTheRealFolders() {
        String text = StartupProblem.describe(new RuntimeException("boom"));
        assertTrue(text.contains("boom"));
        assertTrue(text.contains("data"));
    }
}
