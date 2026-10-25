package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/**
 * MariaDB only (the {@code test-mariadb} CI job): the database itself refuses {@code Mai} next to {@code mai} (TODO 1.9),
 * and an older database without the key gets it on upgrade, unless such a pair already exists.
 */
public class MariaDbLoginNameKeyTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
        Assume.assumeFalse("MariaDB-only test (this run uses SQLite)", DatabaseConfig.isSqlite());
    }

    private static void addUser(Statement st, String name) throws Exception {
        st.executeUpdate("INSERT INTO users(username, password_hash, role, status, created_at) VALUES ('" + name
                + "', 'x', 'EMPLOYEE', 'ACTIVE', NOW())");
    }

    @Test
    public void aNewDatabaseRefusesTwoNamesThatDifferOnlyInCapitals() throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM users WHERE username LIKE 'lkey-%'");
            try {
                addUser(st, "lkey-Mai");
                assertThrows(java.sql.SQLException.class, () -> addUser(st, "lkey-mai"));
                assertThrows(java.sql.SQLException.class, () -> addUser(st, "LKEY-MAI"));
                addUser(st, "lkey-mai2"); // a different name is fine
            } finally {
                st.executeUpdate("DELETE FROM users WHERE username LIKE 'lkey-%'");
            }
        }
    }

    @Test
    public void anOlderDatabaseGetsTheKeyOnUpgradeAndItIsSkippedWhileAPairExists() throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM users WHERE username LIKE 'lkey-%'");
            try {
                st.execute("ALTER TABLE users DROP INDEX " + SchemaMigrations.MARIA_LOGIN_KEY_INDEX);
                st.execute("ALTER TABLE users DROP COLUMN " + SchemaMigrations.MARIA_LOGIN_KEY_COLUMN); // an older database
                addUser(st, "lkey-Sam");
                addUser(st, "lkey-sam"); // the pair the old database could not stop
                assertEquals(List.of("lkey-sam"), SchemaMigrations.ensureMariaLoginNameKey(c));
                st.executeUpdate("DELETE FROM users WHERE username = 'lkey-Sam'"); // fixed by the owner
                assertEquals(List.of(), SchemaMigrations.ensureMariaLoginNameKey(c));
                assertThrows(java.sql.SQLException.class, () -> addUser(st, "LKEY-SAM"));
                assertEquals("running again changes nothing", List.of(), SchemaMigrations.ensureMariaLoginNameKey(c));
            } finally {
                st.executeUpdate("DELETE FROM users WHERE username LIKE 'lkey-%'");
                SchemaMigrations.ensureMariaLoginNameKey(c);
            }
        }
    }
}
