package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TODO 6.75: the row counts of the dashboard's database health line, on the real test database: each number is checked against a
 * count made here, so a count that is not the table's own would fail it. (That the tables exist in both schemas is checked without a
 * database by {@code DatabaseHealthTablesTest}.)
 */
public class DatabaseHealthRowsTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Test
    public void theCountsAreTheRowsOfEachTable() throws Exception {
        try (Connection connection = DatabaseConfig.getConnection()) {
            Map<String, Long> counts = DatabaseHealth.rowCounts(connection);
            assertEquals("every counted table was counted", DatabaseHealth.COUNTED_TABLES.size(), counts.size());
            for (String table : DatabaseHealth.COUNTED_TABLES) {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rs.next();
                    assertEquals(table, rs.getLong(1), counts.get(table).longValue());
                }
            }
        }
    }
}
