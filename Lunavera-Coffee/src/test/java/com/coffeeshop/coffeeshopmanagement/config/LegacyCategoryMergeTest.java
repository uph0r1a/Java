package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TODO 6.7 / 6.22: the old "Ca Phe" category is merged into "Cà phê" once, not at every start. */
public class LegacyCategoryMergeTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.requireSqlite(); // MariaDB's accent-blind collation never lets both names exist
        TestDatabaseSupport.ensureReady();
    }

    private static long count(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    public void theMergeRunsOnceAndThenLeavesALaterCategoryWithThatNameAlone() throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            DatabaseConfig.ensureFlagsTable(c);
            st.executeUpdate("DELETE FROM app_flags WHERE name = '" + DatabaseConfig.LEGACY_CATEGORY_MERGE_FLAG + "'");
            if (count(st, "SELECT COUNT(*) FROM categories WHERE name = 'Cà phê'") == 0) {
                st.executeUpdate("INSERT INTO categories (name, description, active) VALUES ('Cà phê', NULL, 1)");
            }
            st.executeUpdate("INSERT INTO categories (name, description, active) VALUES ('Ca Phe', NULL, 1)");

            DatabaseConfig.mergeLegacyCategoryOnce(c);

            assertEquals("the old category is merged away", 0, count(st, "SELECT COUNT(*) FROM categories WHERE name = 'Ca Phe'"));
            assertEquals("Cà phê is kept", 1, count(st, "SELECT COUNT(*) FROM categories WHERE name = 'Cà phê'"));
            assertEquals("the run is recorded", 1, count(st,
                    "SELECT COUNT(*) FROM app_flags WHERE name = '" + DatabaseConfig.LEGACY_CATEGORY_MERGE_FLAG + "'"));

            // The shop later makes its own category with that name: the finished job must not delete it.
            st.executeUpdate("INSERT INTO categories (name, description, active) VALUES ('Ca Phe', NULL, 1)");
            DatabaseConfig.mergeLegacyCategoryOnce(c);
            assertEquals(1, count(st, "SELECT COUNT(*) FROM categories WHERE name = 'Ca Phe'"));
            assertTrue(count(st, "SELECT COUNT(*) FROM categories WHERE name = 'Cà phê'") >= 1);

            st.executeUpdate("DELETE FROM categories WHERE name = 'Ca Phe'"); // leave the shared test database as it was
        }
    }
}
