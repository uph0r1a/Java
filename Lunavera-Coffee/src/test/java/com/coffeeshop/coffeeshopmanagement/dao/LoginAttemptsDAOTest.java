package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.LoginAttemptStore.Attempts;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** TODO 2A.19 / 2A.27: the wrong-password counters in the database, in calendar time, read back in the throttle's own clock. */
public class LoginAttemptsDAOTest {

    private final AtomicLong wall = new AtomicLong(1_800_000_000_000L);
    private final LoginAttemptsDAO dao = new LoginAttemptsDAO(wall::get);
    private String scope;

    @BeforeClass
    public static void database() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void ownScope() {
        scope = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @After
    public void removeRows() throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("DELETE FROM login_attempts WHERE scope = ?")) {
            st.setString(1, scope);
            st.executeUpdate();
        }
    }

    private int count() throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM login_attempts WHERE scope = ?")) {
            st.setString(1, scope);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void rawRow(String key, int failures, long lastFailure, long blockedUntil) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("INSERT INTO login_attempts (scope, name_key, failures, last_failure, blocked_until) VALUES (?,?,?,?,?)")) {
            st.setString(1, scope);
            st.setString(2, key);
            st.setInt(3, failures);
            st.setLong(4, lastFailure);
            st.setLong(5, blockedUntil);
            st.executeUpdate();
        }
    }

    private long storedBlockedUntil(String key) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT blocked_until FROM login_attempts WHERE scope = ? AND name_key = ?")) {
            st.setString(1, scope);
            st.setString(2, key);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    public void countersAreSavedLoadedAndDeletedAndTheTimesFollowTheCallersClock() {
        assertNull(dao.load(scope, "admin", 1_000));
        // in the caller's clock "now" is 1,000; the last failure was 5 s ago and the wait ends in 7 s
        dao.save(scope, "admin", new Attempts(6, 1_000 - 5_000, true, 1_000 + 7_000), 1_000);
        // the same moment read by another run of the program, whose clock has a different zero
        Attempts back = dao.load(scope, "admin", -90_000);
        assertEquals(6, back.failures());
        assertEquals(-90_000 - 5_000, back.lastFailure());
        assertTrue(back.delayed());
        assertEquals(-90_000 + 7_000, back.blockedUntil());
        // three seconds later (calendar and clocks)
        wall.addAndGet(3_000);
        assertEquals(-90_000 + 4_000, dao.load(scope, "admin", -87_000).blockedUntil() - 3_000);
        dao.delete(scope, "admin");
        assertNull(dao.load(scope, "admin", 0));
        dao.delete(scope, "admin"); // deleting nothing is fine
    }

    @Test
    public void aBucketThatWasNeverDelayedStaysUndelayed() {
        dao.save(scope, "typo", new Attempts(2, 500, false, 0), 1_000);
        Attempts back = dao.load(scope, "typo", 77);
        assertFalse(back.delayed());
        assertEquals(2, back.failures());
    }

    @Test
    public void anAccountBucketWithANulInItsNameAndALongTypedNameBothWork() {
        dao.save(scope, "\0account:7", new Attempts(3, 100, true, 900), 1_000);
        assertEquals(3, dao.load(scope, "\0account:7", 1_000).failures());
        assertNull("a typed name cannot be the account's bucket", dao.load(scope, "account:7", 1_000));
        String longName = "n".repeat(300);
        dao.save(scope, longName, new Attempts(2, 100, false, 0), 1_000);
        assertEquals("a 300-character typed name is cut the same way every time", 2, dao.load(scope, longName, 1_000).failures());
    }

    @Test
    public void theScopesAreSeparate() {
        dao.save(scope, "admin", new Attempts(1, 100, false, 0), 1_000);
        assertNull(dao.load(scope + "x", "admin", 1_000));
    }

    // ---- TODO 2A.27: whatever the table says, the wait is at most 30 s and a bad row is corrected, not re-cut ----

    @Test
    public void aDelayStoredWhileTheCalendarWasYearsAheadIsCutToThirtySecondsAndCorrectedInTheTable() throws SQLException {
        long tenYears = 10L * 365 * 24 * 3600 * 1000;
        rawRow("admin", 40, wall.get() + tenYears, wall.get() + tenYears);
        Attempts first = dao.load(scope, "admin", 5_000);
        assertEquals(5_000 + 30_000, first.blockedUntil());
        assertEquals("a failure from the future counts as just now", 5_000, first.lastFailure());
        assertEquals("the table now holds the corrected value", wall.get() + 30_000, storedBlockedUntil("admin"));

        // 30 seconds later it is over: the ceiling did not slide forward with the calendar
        wall.addAndGet(30_000);
        Attempts later = dao.load(scope, "admin", 35_000);
        assertEquals(35_000, later.blockedUntil());
        // and a quarter of an hour of quiet makes the old failures count as expired
        wall.addAndGet(15 * 60_000);
        Attempts muchLater = dao.load(scope, "admin", 35_000 + 15 * 60_000);
        assertTrue(muchLater.lastFailure() < 35_000 + 15 * 60_000 - LoginAttemptStore.FORGET_AFTER_MILLIS);
    }

    @Test
    public void aCalendarMovedBackNeverMakesAnAgeNegative() {
        dao.save(scope, "admin", new Attempts(4, 1_000, true, 3_000), 1_000);
        wall.addAndGet(-3_600_000); // the PC clock was set back an hour
        Attempts back = dao.load(scope, "admin", 1_000);
        assertEquals("treated as a failure just now", 1_000, back.lastFailure());
        assertTrue(back.blockedUntil() - 1_000 <= LoginAttemptStore.MAX_DELAY_MILLIS);
    }

    // ---- TODO 2A.31: a flood of made-up names does not erase the delay of a real account, and the table stays small ----

    @Test
    public void aFloodOfMadeUpNamesKeepsTheDelayOfTheAttackedAccountAndTheTableSmall() throws SQLException {
        long mono = 10_000;
        dao.save(scope, "admin", new Attempts(8, mono - 1_000, true, mono + 16_000), mono);
        for (int i = 0; i < LoginAttemptStore.MAX_TRACKED + 150; i++) {
            dao.save(scope, "junk" + i, new Attempts(1, mono, false, 0), mono);
        }
        assertNotNull("the account that is being guessed at stays", dao.load(scope, "admin", mono));
        assertTrue("never more than the cap in the whole table", count() <= LoginAttemptStore.MAX_TRACKED);
        assertNotNull("the newest name is kept too", dao.load(scope, "junk" + (LoginAttemptStore.MAX_TRACKED + 149), mono));
    }

    @Test
    public void namesThatExpiredAreForgottenWhenANewOneComesIn() {
        dao.save(scope, "old", new Attempts(5, 0, true, 1), 3_600_000L); // last failure an hour ago
        dao.save(scope, "new", new Attempts(1, 3_600_000L, false, 0), 3_600_000L);
        assertNull("an hour without a wrong attempt: gone", dao.load(scope, "old", 3_600_000L));
        assertNotNull(dao.load(scope, "new", 3_600_000L));
    }
}
