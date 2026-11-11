package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TODO 6.6: a new column goes in a numbered step, not into the start-up list that only ever grew. */
public class LaterColumnsAreFrozenTest {

    @Test
    public void theStartUpListDoesNotGrow() {
        assertEquals("A new column goes in a numbered step: SchemaMigrations.addColumns, registered in SchemaMigrations.SQLITE_STEPS and "
                        + "MariaDbMigrations.STEPS under a new version, plus the CREATE TABLE of both schema classes (docs/DATABASE.md). "
                        + "Do not add it to SqliteSchema.LATER_COLUMNS.",
                SqliteSchema.LATER_COLUMNS_FROZEN, SqliteSchema.LATER_COLUMNS.size());
    }

    @Test
    public void noColumnIsListedTwice() {
        Set<String> seen = new HashSet<>();
        for (String[] column : SqliteSchema.LATER_COLUMNS) {
            assertTrue(column[0] + "." + column[1] + " twice", seen.add(column[0] + "." + column[1]));
        }
    }

    @Test
    public void aMariaDbStepExistsForEverySqliteStepAfterTwo() {
        // Version 2 (SQLite indexes) is the only SQLite step MariaDB does not need: its tables are made with them.
        for (int version : SchemaMigrations.SQLITE_STEPS.keySet()) {
            if (version > 3) {
                assertTrue("MariaDbMigrations.STEPS has no step " + version, MariaDbMigrations.STEPS.containsKey(version));
            }
        }
    }
}
