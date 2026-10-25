package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TODO 2A.25: events that repeat go through {@code SecurityLog.warnLimited}, one line a minute per kind. */
public class SecurityLogTest {

    private final List<LogRecord> records = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private final Logger logger = Logger.getLogger("lunavera.security");

    @Before
    public void setUp() {
        SecurityLog.resetLimitsForTests();
        logger.addHandler(capture);
    }

    @After
    public void tearDown() {
        logger.removeHandler(capture);
    }

    @Test
    public void aRepeatedWarningIsWrittenOnceAMinute() {
        for (int i = 0; i < 100; i++) {
            SecurityLog.warnLimited("wrongpw:admin", "failed login: wrong password for 'admin'");
        }
        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        assertEquals("SECURITY: failed login: wrong password for 'admin'", records.get(0).getMessage());
    }

    @Test
    public void theKeyDoesNotCareAboutCapitals() {
        SecurityLog.warnLimited("blocked:Admin", "a");
        SecurityLog.warnLimited("BLOCKED:admin", "b");
        assertEquals(1, records.size());
    }

    @Test
    public void differentKindsAreAllWritten() {
        SecurityLog.warnLimited("wrongpw:admin", "a");
        SecurityLog.warnLimited("blocked:admin", "b");
        SecurityLog.warnLimited("nouser:admin", "c");
        assertEquals(3, records.size());
    }

    @Test
    public void aDifferentNameEveryTimeCannotFloodTheLog() {
        for (int i = 0; i < 500; i++) {
            SecurityLog.warnLimited("nouser:name" + i, "failed login: no such user 'name" + i + "'");
        }
        assertTrue("at most 30 lines a minute in all, got " + records.size(), records.size() <= 30);
        assertTrue(records.size() >= 1);
    }

    @Test
    public void aPlainWarningAndAnEventAreNeverHeldBack() {
        for (int i = 0; i < 5; i++) {
            SecurityLog.warn("discount refused");
            SecurityLog.event("account locked");
        }
        assertEquals(10, records.size());
    }

    @Test
    public void aNullKeyIsAKeyLikeAnyOther() {
        SecurityLog.warnLimited(null, "a");
        SecurityLog.warnLimited(null, "b");
        assertEquals(1, records.size());
    }
}
