package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SqlDialectTest {

    @Test
    public void typeSettingMapsToTheRightDialect() {
        assertEquals("blank means the default, MariaDB", SqlDialect.MARIADB, SqlDialect.fromSetting(null));
        assertEquals(SqlDialect.MARIADB, SqlDialect.fromSetting(""));
        assertEquals(SqlDialect.SQLITE, SqlDialect.fromSetting("sqlite"));
        assertEquals(SqlDialect.MARIADB, SqlDialect.fromSetting("MariaDB"));
        assertEquals(SqlDialect.MARIADB, SqlDialect.fromSetting("mysql"));
    }

    @Test
    public void insertIgnoreIsSpelledPerDatabase() {
        assertEquals("INSERT OR IGNORE INTO app_flags", SqlDialect.SQLITE.insertIgnoreInto("app_flags"));
        assertEquals("INSERT IGNORE INTO app_flags", SqlDialect.MARIADB.insertIgnoreInto("app_flags"));
    }

    @Test
    public void caseInsensitiveOrderingIsSpelledPerDatabase() {
        assertEquals("e.full_name COLLATE NOCASE", SqlDialect.SQLITE.caseInsensitive("e.full_name"));
        assertEquals("e.full_name COLLATE utf8mb4_unicode_ci", SqlDialect.MARIADB.caseInsensitive("e.full_name"));
    }

    @Test
    public void theFlagsTableCanBeCreatedInBothDatabases() {
        String sqlite = SqlDialect.SQLITE.createFlagsTable();
        String maria = SqlDialect.MARIADB.createFlagsTable();
        assertTrue(sqlite.contains("TEXT PRIMARY KEY"));
        // MySQL cannot make a plain TEXT column a key: it needs a length
        assertFalse(maria, maria.contains("TEXT PRIMARY KEY"));
        assertTrue(maria, maria.contains("VARCHAR(100)"));
        assertTrue(maria, maria.contains("utf8mb4"));
    }

    @Test
    public void theTableExistsQueriesTakeExactlyOneParameter() {
        for (SqlDialect d : SqlDialect.values()) {
            String sql = d.tableExistsQuery();
            assertEquals(sql, 1, sql.chars().filter(ch -> ch == '?').count());
        }
        assertTrue(SqlDialect.MARIADB.tableExistsQuery().contains("DATABASE()"));
    }
}
