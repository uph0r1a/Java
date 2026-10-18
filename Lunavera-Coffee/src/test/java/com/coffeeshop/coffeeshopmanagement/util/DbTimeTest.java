package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.config.SqlDialect;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DbTimeTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 11, 14, 30, 5, 123_456_789);

    @Test
    public void sqliteKeepsTheIsoTextItAlwaysHad() {
        assertEquals("2026-10-11T14:30:05.123456789", DbTime.format(T, SqlDialect.SQLITE));
        assertEquals("2026-10-11T14:30", DbTime.format(LocalDateTime.of(2026, 10, 11, 14, 30), SqlDialect.SQLITE));
    }

    @Test
    public void mariaDbGetsWholeSecondsWithASpace() {
        assertEquals("2026-10-11 14:30:05", DbTime.format(T, SqlDialect.MARIADB));
        // LocalDateTime.toString() would drop the seconds here; DATETIME text must always have them
        assertEquals("2026-10-11 14:30:00", DbTime.format(LocalDateTime.of(2026, 10, 11, 14, 30), SqlDialect.MARIADB));
    }

    @Test
    public void readsEveryShapeEitherDatabaseCanGive() {
        LocalDateTime wholeSeconds = LocalDateTime.of(2026, 10, 11, 14, 30, 5);
        assertEquals(wholeSeconds, DbTime.parse("2026-10-11T14:30:05"));
        assertEquals(wholeSeconds, DbTime.parse("2026-10-11 14:30:05"));
        assertEquals(wholeSeconds, DbTime.parse("2026-10-11 14:30:05.0"));
        assertEquals(wholeSeconds, DbTime.parse("  2026-10-11 14:30:05.000000 "));
        assertEquals(LocalDateTime.of(2026, 10, 11, 14, 30), DbTime.parse("2026-10-11T14:30"));
        assertEquals(T, DbTime.parse("2026-10-11T14:30:05.123456789"));
    }

    @Test
    public void aDateWithoutATimeIsMidnight() {
        assertEquals(LocalDateTime.of(2026, 10, 11, 0, 0), DbTime.parse("2026-10-11"));
    }

    @Test
    public void nothingStaysNothing() {
        assertNull(DbTime.parse(null));
        assertNull(DbTime.parse("   "));
        assertNull(DbTime.toSqlDateTime(null));
    }

    @Test
    public void whatIsWrittenIsReadBackForBothDatabases() {
        LocalDateTime whole = T.withNano(0);
        for (SqlDialect d : SqlDialect.values()) {
            assertEquals(d.name(), whole, DbTime.parse(DbTime.format(whole, d)));
        }
    }

    @Test
    public void importConvertsOldSqliteTextToDatetimeText() {
        assertEquals("2026-10-11 14:30:05", DbTime.toSqlDateTime("2026-10-11T14:30:05.123456789"));
        assertEquals("2026-10-11 14:30:00", DbTime.toSqlDateTime("2026-10-11T14:30"));
        assertEquals("2026-10-11 00:00:00", DbTime.toSqlDateTime("2026-10-11"));
        assertEquals("2026-10-11 14:30:05", DbTime.toSqlDateTime("2026-10-11 14:30:05"));
    }
}
