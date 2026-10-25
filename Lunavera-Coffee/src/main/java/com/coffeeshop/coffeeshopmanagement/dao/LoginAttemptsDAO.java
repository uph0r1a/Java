package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The wrong-password counters in table {@code login_attempts} (TODO 2A.19), for SQLite and MariaDB alike. With MariaDB the
 * delay is shared by every till, which also closes the "try a few passwords at each till" way round it.
 *
 * <p>The table holds calendar times (epoch milliseconds); this class converts them from and to the throttle's monotonic
 * clock (see {@link LoginAttemptStore}). What it reads back is made safe before the throttle sees it (TODO 2A.27): a delay
 * is never longer than {@value LoginAttemptStore#MAX_DELAY_MILLIS} ms and a failure is never in the future, whatever the
 * calendar said when the row was written, and a row that breaks those rules is <b>corrected in the table</b> at once, because
 * cutting it again on every read would move the ceiling forward with the calendar and the delay would never end.
 *
 * <p>No permission check on purpose: the sign-in runs while nobody is signed in.
 */
public final class LoginAttemptsDAO implements LoginAttemptStore {

    /** The table has {@code VARCHAR(100)} for the name; a longer text is cut, the throttle only needs it to be stable. */
    static final int MAX_KEY_LENGTH = 100;

    private final LongSupplier calendar;

    public LoginAttemptsDAO() {
        this(System::currentTimeMillis);
    }

    /** For tests: the calendar (epoch milliseconds) is given. */
    public LoginAttemptsDAO(LongSupplier calendarMillis) {
        this.calendar = calendarMillis;
    }

    /**
     * The bucket name as stored: cut to the column, and with the NUL character the throttle puts in front of an account's bucket
     * written as character 1 (a NUL does not travel safely through every driver; both are control characters, which a typed
     * name can never contain, so the two can never clash).
     */
    static String cut(String key) {
        String k = key == null ? "" : key.replace('\0', '\u0001');
        return k.length() > MAX_KEY_LENGTH ? k.substring(0, MAX_KEY_LENGTH) : k;
    }

    @Override
    public Attempts load(String scope, String key, long nowMono) {
        String name = cut(key);
        String sql = "SELECT failures, last_failure, blocked_until FROM login_attempts WHERE scope = ? AND name_key = ?";
        try (Connection connection = DatabaseConfig.getConnection()) {
            int failures;
            long lastFailure;
            long blockedUntil;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, scope);
                statement.setString(2, name);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    failures = rs.getInt(1);
                    lastFailure = rs.getLong(2);
                    blockedUntil = rs.getLong(3);
                }
            }
            long wall = calendar.getAsLong();
            long saneLast = Math.min(lastFailure, wall);
            long saneBlocked = blockedUntil == 0 ? 0 : Math.min(blockedUntil, wall + MAX_DELAY_MILLIS);
            if (saneLast != lastFailure || saneBlocked != blockedUntil) {
                update(connection, scope, name, failures, saneLast, saneBlocked); // pin the corrected values (see the class comment)
            }
            long age = Math.min(Math.max(wall - saneLast, 0), FORGET_AFTER_MILLIS + 1);
            boolean delayed = saneBlocked != 0;
            long remaining = delayed ? Math.min(Math.max(saneBlocked - wall, 0), MAX_DELAY_MILLIS) : 0;
            return new Attempts(failures, nowMono - age, delayed, nowMono + remaining);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the login attempts", e);
        }
    }

    @Override
    public void save(String scope, String key, Attempts attempts, long nowMono) {
        String name = cut(key);
        long wall = calendar.getAsLong();
        long lastFailure = wall - Math.max(0, nowMono - attempts.lastFailure());
        long blockedUntil = attempts.delayed() ? wall + Math.min(Math.max(attempts.blockedUntil() - nowMono, 0), MAX_DELAY_MILLIS) : 0;
        if (attempts.delayed() && blockedUntil == 0) {
            blockedUntil = 1; // "delayed, and the wait is over" must not read back as "never delayed"
        }
        try (Connection connection = DatabaseConfig.getConnection()) {
            if (update(connection, scope, name, attempts.failures(), lastFailure, blockedUntil) == 0) {
                try {
                    insert(connection, scope, name, attempts.failures(), lastFailure, blockedUntil);
                } catch (SQLException e) {
                    if (!UniqueViolation.isUnique(e)) {
                        throw e;
                    }
                    update(connection, scope, name, attempts.failures(), lastFailure, blockedUntil); // another till inserted it a moment ago
                }
                trim(connection, scope, name, wall);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save the login attempts", e);
        }
    }

    @Override
    public void delete(String scope, String key) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM login_attempts WHERE scope = ? AND name_key = ?")) {
            statement.setString(1, scope);
            statement.setString(2, cut(key));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to clear the login attempts", e);
        }
    }

    private static int update(Connection connection, String scope, String name, int failures, long lastFailure, long blockedUntil)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE login_attempts SET failures = ?, last_failure = ?, blocked_until = ? WHERE scope = ? AND name_key = ?")) {
            statement.setInt(1, failures);
            statement.setLong(2, lastFailure);
            statement.setLong(3, blockedUntil);
            statement.setString(4, scope);
            statement.setString(5, name);
            return statement.executeUpdate();
        }
    }

    private static void insert(Connection connection, String scope, String name, int failures, long lastFailure, long blockedUntil)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO login_attempts (scope, name_key, failures, last_failure, blocked_until) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, scope);
            statement.setString(2, name);
            statement.setInt(3, failures);
            statement.setLong(4, lastFailure);
            statement.setLong(5, blockedUntil);
            statement.executeUpdate();
        }
    }

    /**
     * Runs when a bucket is added: forgets the ones that expired, and when more than {@link #MAX_TRACKED} remain drops those
     * with the fewest wrong attempts first (then the oldest), never the one just stored and never everything at once
     * (TODO 2A.31: flooding the screen with made-up names must not erase the delay of a real account).
     */
    private static void trim(Connection connection, String scope, String justStored, long wall) throws SQLException {
        try (PreparedStatement expired = connection.prepareStatement("DELETE FROM login_attempts WHERE last_failure < ?")) {
            expired.setLong(1, wall - FORGET_AFTER_MILLIS);
            expired.executeUpdate();
        }
        int count;
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM login_attempts");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            count = rs.getInt(1);
        }
        if (count <= MAX_TRACKED) {
            return;
        }
        List<String[]> victims = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT scope, name_key FROM login_attempts WHERE NOT (scope = ? AND name_key = ?) "
                        + "ORDER BY (CASE WHEN blocked_until > ? THEN 1 ELSE 0 END) ASC, failures ASC, last_failure ASC LIMIT ?")) {
            statement.setString(1, scope);
            statement.setString(2, justStored);
            statement.setLong(3, wall);
            statement.setInt(4, count - MAX_TRACKED);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    victims.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM login_attempts WHERE scope = ? AND name_key = ?")) {
            for (String[] victim : victims) {
                delete.setString(1, victim[0]);
                delete.setString(2, victim[1]);
                delete.addBatch();
            }
            delete.executeBatch();
        }
    }
}
