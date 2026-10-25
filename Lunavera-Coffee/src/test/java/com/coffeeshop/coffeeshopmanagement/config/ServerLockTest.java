package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * TODO 3.50 / 3.51: the server locks that keep two tills from migrating together and make one till the snapshot maker.
 * A stand-in connection that answers like MariaDB's {@code GET_LOCK}, so no server is needed.
 */
public class ServerLockTest {

    /** A fake connection: {@code answer} is what GET_LOCK returns (1, 0 or null); {@code sql} records what was asked. */
    private static final class Fake {
        final List<String> sql = new ArrayList<>();
        final List<Object> params = new ArrayList<>();
        Object answer;
        boolean valid = true;
        boolean closed;
        Connection connection;

        Fake(Object answer) {
            this.answer = answer;
            InvocationHandler resultSet = (p, m, a) -> switch (m.getName()) {
                case "next" -> true;
                case "getObject" -> this.answer;
                case "close" -> null;
                default -> throw new UnsupportedOperationException(m.getName());
            };
            ResultSet rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, resultSet);
            InvocationHandler statement = (p, m, a) -> switch (m.getName()) {
                case "setString", "setInt" -> { params.add(a[1]); yield null; }
                case "executeQuery" -> rs;
                case "close" -> null;
                default -> throw new UnsupportedOperationException(m.getName());
            };
            InvocationHandler conn = (p, m, a) -> switch (m.getName()) {
                case "prepareStatement" -> {
                    sql.add((String) a[0]);
                    yield Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, statement);
                }
                case "isValid" -> valid;
                case "isClosed" -> closed;
                case "close" -> { closed = true; yield null; }
                default -> throw new UnsupportedOperationException(m.getName());
            };
            connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, conn);
        }
    }

    @After
    public void clean() {
        SqliteSnapshotService.forgetLeader();
    }

    @Test
    public void acquireIsTrueOnlyWhenTheServerAnswersOne() throws Exception {
        Fake got = new Fake(1L);
        assertTrue(ServerLock.acquire(got.connection, "lunavera-migrate", 60));
        assertEquals(List.of("SELECT GET_LOCK(?, ?)"), got.sql);
        assertEquals(List.of("lunavera-migrate", 60), got.params);
        assertFalse("another till holds it", ServerLock.acquire(new Fake(0L).connection, "x", 0));
        assertFalse("the server could not say (NULL)", ServerLock.acquire(new Fake(null).connection, "x", 0));
    }

    @Test
    public void releaseAsksTheServerAndNeverThrows() {
        Fake fake = new Fake(1L);
        ServerLock.release(fake.connection, "lunavera-migrate");
        assertEquals(List.of("SELECT RELEASE_LOCK(?)"), fake.sql);
        Connection broken = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> { throw new java.sql.SQLException("gone"); });
        ServerLock.release(broken, "x"); // must not throw: the lock goes away with the connection
    }

    @Test
    public void theFirstTillBecomesTheSnapshotMakerAndKeepsTheLock() throws Exception {
        Fake mine = new Fake(1L);
        assertTrue(SqliteSnapshotService.isLeader(() -> mine.connection));
        assertFalse("the lock connection stays open", mine.closed);
        // asked again an hour later: no new connection is needed
        assertTrue(SqliteSnapshotService.isLeader(() -> { throw new AssertionError("must reuse the held connection"); }));
    }

    @Test
    public void aSecondTillSkipsAndClosesItsConnection() throws Exception {
        Fake other = new Fake(0L);
        assertFalse(SqliteSnapshotService.isLeader(() -> other.connection));
        assertTrue("no connection is left open for nothing", other.closed);
    }

    @Test
    public void aDeadLockConnectionIsReplacedOrGivenUp() throws Exception {
        Fake first = new Fake(1L);
        assertTrue(SqliteSnapshotService.isLeader(() -> first.connection));
        first.valid = false; // the server was restarted: the lock is gone with the connection
        Fake taken = new Fake(0L); // and another till has the lock now
        assertFalse(SqliteSnapshotService.isLeader(() -> taken.connection));
        assertTrue(first.closed);
        assertTrue(taken.closed);
        Fake again = new Fake(1L); // later the lock is free: this till takes it again
        assertTrue(SqliteSnapshotService.isLeader(() -> again.connection));
    }

    @Test
    public void snapshotsSettingDefaultsToOnAndCanBeSwitchedOff() {
        Properties file = new Properties();
        assertTrue(DatabaseSettings.snapshotsEnabled(file, key -> null));
        file.setProperty("db.snapshots", "false");
        assertFalse(DatabaseSettings.snapshotsEnabled(file, key -> null));
        assertTrue("a system property wins", DatabaseSettings.snapshotsEnabled(file, key -> key.equals("lunavera.db.snapshots") ? "true" : null));
        file.setProperty("db.snapshots", "yes");
        assertTrue(DatabaseSettings.snapshotsEnabled(file, key -> null));
        file.setProperty("db.snapshots", "maybe");
        try {
            DatabaseSettings.snapshotsEnabled(file, key -> null);
            fail("a value that is no yes/no is refused");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }
}
