package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.LoginAttemptStore;
import com.coffeeshop.coffeeshopmanagement.dao.LoginAttemptsDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * TODO 2A.19 / 2A.27: the throttle with its counters in the database. A "restart" is a new throttle with a clock whose zero
 * is somewhere else (the real one is monotonic, so every run of the program has its own zero) on the same table.
 */
public class LoginThrottleStoreTest {

    private final AtomicLong wall = new AtomicLong(1_800_000_000_000L);
    private final AtomicLong mono = new AtomicLong(5_000_000L);
    private final AtomicLong otherMono = new AtomicLong(-123_456_789L); // the clock of the next run: another zero
    private String scope;

    @BeforeClass
    public static void database() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void ownScope() {
        scope = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @After
    public void removeRows() throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("DELETE FROM login_attempts WHERE scope = ?")) {
            st.setString(1, scope);
            st.executeUpdate();
        }
    }

    private void pass(long millis) {
        wall.addAndGet(millis);
        mono.addAndGet(millis);
        otherMono.addAndGet(millis);
    }

    private LoginThrottle running() {
        return new LoginThrottle(mono::get, new LoginAttemptsDAO(wall::get), scope);
    }

    private LoginThrottle afterRestart() {
        return new LoginThrottle(otherMono::get, new LoginAttemptsDAO(wall::get), scope);
    }

    @Test
    public void aRestartKeepsTheDelayAndTheCountGoesOn() {
        LoginThrottle before = running();
        for (int i = 0; i < 6; i++) before.recordFailure("admin");
        assertEquals(8, before.secondsUntilAllowed("admin"));

        LoginThrottle restarted = afterRestart();
        assertEquals("closing and reopening the app must not clear the delay", 8, restarted.secondsUntilAllowed("admin"));
        pass(3_000);
        assertEquals(5, restarted.secondsUntilAllowed("admin"));
        pass(5_000);
        assertEquals(0, restarted.secondsUntilAllowed("admin"));
        restarted.recordFailure("admin");
        assertEquals("the seventh wrong attempt, not a new count", 16, restarted.secondsUntilAllowed("admin"));
    }

    @Test
    public void theWaitOfAnAccountSurvivesToo() {
        LoginThrottle before = running();
        for (int i = 0; i < 6; i++) before.recordFailureForAccount(7);
        assertEquals(8, afterRestart().secondsUntilAllowedForAccount(7));
        assertEquals(0, afterRestart().secondsUntilAllowedForAccount(8));
    }

    @Test
    public void aCorrectLoginClearsTheStoredCountersForGood() {
        LoginThrottle before = running();
        for (int i = 0; i < 6; i++) before.recordFailure("admin");
        before.recordSuccess("admin");
        assertEquals(0, afterRestart().secondsUntilAllowed("admin"));
        for (int i = 0; i < 6; i++) before.recordFailureForAccount(7);
        before.recordSuccessForAccount(7);
        assertEquals(0, afterRestart().secondsUntilAllowedForAccount(7));
    }

    @Test
    public void twoTillsOnTheSameDatabaseSeeTheSameDelay() {
        LoginThrottle tillOne = running();
        LoginThrottle tillTwo = afterRestart(); // another program, another clock zero, alive at the same time
        assertEquals(0, tillTwo.secondsUntilAllowed("admin"));
        for (int i = 0; i < 6; i++) tillOne.recordFailure("admin");
        assertEquals("a few guesses at each till do not add up to more guesses", 8, tillTwo.secondsUntilAllowed("admin"));
        tillTwo.recordFailure("admin"); // counts on from the other till's six
        assertEquals(16, tillOne.secondsUntilAllowed("admin"));
    }

    @Test
    public void thePasswordChangeScopeIsSeparateFromTheSignIn() {
        LoginThrottle signIn = running();
        for (int i = 0; i < 9; i++) signIn.recordFailure("admin");
        assertEquals(30, signIn.secondsUntilAllowed("admin"));
        LoginThrottle other = new LoginThrottle(mono::get, new LoginAttemptsDAO(wall::get), scope + "p");
        assertEquals(0, other.secondsUntilAllowed("admin"));
    }

    // ---- TODO 2A.27 ----

    @Test
    public void aDelayStoredWhileTheCalendarWasYearsAheadEndsAfterThirtySeconds() throws SQLException {
        long tenYears = 10L * 365 * 24 * 3600 * 1000;
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("INSERT INTO login_attempts (scope, name_key, failures, last_failure, blocked_until) VALUES (?,?,?,?,?)")) {
            st.setString(1, scope);
            st.setString(2, "admin");
            st.setInt(3, 40);
            st.setLong(4, wall.get() + tenYears);
            st.setLong(5, wall.get() + tenYears);
            st.executeUpdate();
        }
        LoginThrottle throttle = afterRestart();
        assertEquals(30, throttle.secondsUntilAllowed("admin"));
        pass(30_000);
        assertEquals("the ceiling did not slide forward", 0, afterRestart().secondsUntilAllowed("admin"));
        pass(16 * 60_000);
        LoginThrottle later = afterRestart();
        later.recordFailure("admin");
        assertEquals("a quiet quarter of an hour forgot the count of 40", 0, later.secondsUntilAllowed("admin"));
    }

    @Test
    public void anAttackerTypingForAnHourNeverMakesTheOwnerWaitMoreThanThirtySeconds() {
        LoginThrottle throttle = running();
        for (int i = 0; i < 180; i++) { // a wrong password for "admin" every 20 seconds
            throttle.recordFailure("admin");
            assertTrue(throttle.secondsUntilAllowed("admin") <= 30);
            pass(20_000);
        }
        pass(30_000);
        assertEquals("free again 30 seconds after the attacker stops", 0, afterRestart().secondsUntilAllowed("admin"));
    }

    // ---- a broken database must not stop a sign-in or switch the delay off ----

    @Test
    public void whenTheDatabaseFailsTheDelayCarriesOnInMemory() {
        LoginAttemptStore broken = new LoginAttemptStore() {
            @Override public Attempts load(String scope, String key, long nowMono) { throw new RuntimeException("database down"); }
            @Override public void save(String scope, String key, Attempts attempts, long nowMono) { throw new RuntimeException("database down"); }
            @Override public void delete(String scope, String key) { throw new RuntimeException("database down"); }
        };
        LoginThrottle throttle = new LoginThrottle(mono::get, broken, scope);
        for (int i = 0; i < 6; i++) throttle.recordFailure("admin");
        assertEquals(8, throttle.secondsUntilAllowed("admin"));
        throttle.recordSuccess("admin");
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }
}
