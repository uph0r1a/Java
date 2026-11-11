package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 6.77: the two helpers a test uses so that it does not leave the shared database changed ({@code freshDays}, {@code snapshot}). */
public class TestDatabaseSupportHelpersTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Test
    public void freshDaysAreInTheYear3000AndNeverOverlapAndLeaveAGap() {
        LocalDate a = TestDatabaseSupport.freshDays(31);
        LocalDate b = TestDatabaseSupport.freshDays(1);
        LocalDate c = TestDatabaseSupport.freshDay();
        assertTrue(a.getYear() >= 3000);
        assertTrue("the second block starts after the first one ends, with a gap", b.isAfter(a.plusDays(31 + 6)));
        assertTrue(c.isAfter(b.plusDays(1 + 6)));
    }

    @Test
    public void freshDaysFromManyThreadsAreAllDifferent() throws Exception {
        java.util.Set<LocalDate> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int k = 0; k < 50; k++) seen.add(TestDatabaseSupport.freshDay());
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        assertEquals(8 * 50, seen.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlockOfNoDaysIsRefused() {
        TestDatabaseSupport.freshDays(0);
    }

    private static String value(Connection c, String name) throws Exception {
        try (PreparedStatement p = c.prepareStatement("SELECT value FROM settings WHERE name = ?")) {
            p.setString(1, name);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void put(Connection c, String name, String value) throws Exception {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO settings (name, value) VALUES (?, ?)")) {
            p.setString(1, name);
            p.setString(2, value);
            p.executeUpdate();
        }
    }

    private static long count(Connection c) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM settings")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    public void aSnapshotPutsBackAChangedAnAddedAndADeletedRow() throws Exception {
        String keep = "zz.snapshot." + UUID.randomUUID();
        String gone = "zz.snapshot." + UUID.randomUUID();
        String added = "zz.snapshot." + UUID.randomUUID();
        long before;
        try (Connection c = DatabaseConfig.getConnection()) {
            put(c, keep, "one");
            put(c, gone, "two");
            before = count(c);
        }
        TestDatabaseSupport.Snapshot snapshot = TestDatabaseSupport.snapshot("settings");
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement p = c.prepareStatement("UPDATE settings SET value = 'changed' WHERE name = ?")) {
            p.setString(1, keep);
            p.executeUpdate();
            try (PreparedStatement d = c.prepareStatement("DELETE FROM settings WHERE name = ?")) {
                d.setString(1, gone);
                d.executeUpdate();
            }
            put(c, added, "three");
        }
        snapshot.restore();
        try (Connection c = DatabaseConfig.getConnection()) {
            assertEquals("one", value(c, keep));
            assertEquals("two", value(c, gone));
            assertNull(value(c, added));
            assertEquals("every other row is as it was", before, count(c));
            try (PreparedStatement d = c.prepareStatement("DELETE FROM settings WHERE name LIKE 'zz.snapshot.%'")) {
                d.executeUpdate();
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void onlyATableNameIsAcceptedNotASentence() {
        TestDatabaseSupport.snapshot("settings; DROP TABLE users");
    }
}
