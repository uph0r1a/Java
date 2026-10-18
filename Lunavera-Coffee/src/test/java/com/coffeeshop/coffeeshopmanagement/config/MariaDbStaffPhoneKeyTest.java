package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/** MariaDB only: an older database without the staff-phone key gets it on upgrade (TODO 2.17). */
public class MariaDbStaffPhoneKeyTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
        Assume.assumeFalse("MariaDB-only test (this run uses SQLite)", DatabaseConfig.isSqlite());
    }

    @Test
    public void theKeyIsBuiltOnUpgradeAndSkippedWhileDuplicatesExist() throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM employees WHERE full_name LIKE 'key-test-%'");
            try {
                st.execute("ALTER TABLE employees DROP INDEX ux_employees_phone"); // what an older database looks like
                st.executeUpdate("INSERT INTO employees(full_name, phone, active) VALUES ('key-test-1', '0933000111', 1), "
                        + "('key-test-2', '0933000111', 1), ('key-test-3', '', 1), ('key-test-4', '', 1)");
                assertEquals(List.of("0933000111"), SchemaMigrations.ensureMariaEmployeePhoneKey(c));
                st.executeUpdate("DELETE FROM employees WHERE full_name = 'key-test-2'"); // fixed by the owner
                assertEquals(List.of(), SchemaMigrations.ensureMariaEmployeePhoneKey(c));
                assertEquals("blank phones became NULL", 2, count(st, "SELECT COUNT(*) FROM employees WHERE full_name IN "
                        + "('key-test-3','key-test-4') AND phone IS NULL"));
                assertThrows(java.sql.SQLException.class, () -> st.executeUpdate(
                        "INSERT INTO employees(full_name, phone, active) VALUES ('key-test-5', '0933000111', 1)"));
                assertEquals("running again changes nothing", List.of(), SchemaMigrations.ensureMariaEmployeePhoneKey(c));
            } finally {
                st.executeUpdate("DELETE FROM employees WHERE full_name LIKE 'key-test-%'");
                SchemaMigrations.ensureMariaEmployeePhoneKey(c);
            }
        }
    }

    private static int count(Statement st, String sql) throws Exception {
        try (java.sql.ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
