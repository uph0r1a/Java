package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/** TODO 3.78: a date typed with a two-digit year is 20xx, never the year 26; what cannot be read is refused so the old text comes back. */
public class DateTextConverterTest {

    private final DateTextConverter c = new DateTextConverter();

    private void refused(String text) {
        try {
            c.fromString(text);
            fail("should not read: " + text);
        } catch (DateTimeParseException expected) {
            // the date box puts the old text back
        }
    }

    @Test
    public void aTwoDigitYearIsTwentyXx() {
        assertEquals(LocalDate.of(2026, 11, 1), c.fromString("1/11/26"));
        assertEquals(LocalDate.of(2005, 3, 9), c.fromString("09/03/05"));
        assertEquals(LocalDate.of(2000, 1, 1), c.fromString("1/1/00"));
    }

    @Test
    public void aFullDateAndOtherSeparatorsAreRead() {
        assertEquals(LocalDate.of(2026, 11, 1), c.fromString("01/11/2026"));
        assertEquals(LocalDate.of(2026, 11, 1), c.fromString("1-11-2026"));
        assertEquals(LocalDate.of(2026, 11, 1), c.fromString(" 1.11.2026 "));
        assertEquals(LocalDate.of(2026, 11, 1), c.fromString("1 11 2026"));
        assertEquals(LocalDate.of(2026, 11, 1), c.fromString("1/11/2026​"));
    }

    @Test
    public void whatIsNotADateIsRefused() {
        refused("31/02/2026");
        refused("32/01/2026");
        refused("1/13/2026");
        refused("1/11");
        refused("1/11/202");
        refused("1/11/20266");
        refused("a/b/c");
        refused("1/11/2026/5");
        refused("2026-11-01x");
    }

    @Test
    public void emptyTextIsNoDate() {
        assertNull(c.fromString(""));
        assertNull(c.fromString("   "));
        assertNull(c.fromString(null));
        assertNull(c.fromString("​"));
    }

    @Test
    public void aDateIsShownWithAFourDigitYear() {
        assertEquals("01/11/2026", c.toString(LocalDate.of(2026, 11, 1)));
        assertEquals("", c.toString(null));
        assertEquals(LocalDate.of(2026, 11, 1), c.fromString(c.toString(LocalDate.of(2026, 11, 1))));
    }
}
