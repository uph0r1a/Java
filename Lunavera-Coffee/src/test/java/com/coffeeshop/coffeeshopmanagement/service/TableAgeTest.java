package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

/** TODO 5.11: how long a table has been occupied, and when it is highlighted. */
public class TableAgeTest {

    private String before;

    @Before
    public void useVietnamese() {
        before = I18n.current();
        I18n.setLanguage(I18n.VI);
    }

    @After
    public void restoreLanguage() {
        I18n.setLanguage(before);
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 15, 0);

    @Test
    public void theAgeIsTheTimeSinceTheOrderWasOpened() {
        assertEquals(Duration.ofMinutes(35), TableAge.since(NOW.minusMinutes(35), NOW));
        assertNull(TableAge.since(null, NOW));
    }

    @Test
    public void aClockThatRanAheadGivesZeroNotANegativeAge() {
        assertEquals(Duration.ZERO, TableAge.since(NOW.plusHours(3), NOW));
    }

    @Test
    public void theTextSaysMinutesThenHours() {
        assertEquals("vừa mở", TableAge.text(Duration.ofSeconds(40)));
        assertEquals("1 phút", TableAge.text(Duration.ofSeconds(61)));
        assertEquals("35 phút", TableAge.text(Duration.ofMinutes(35)));
        assertEquals("59 phút", TableAge.text(Duration.ofMinutes(59)));
        assertEquals("1 giờ", TableAge.text(Duration.ofMinutes(60)));
        assertEquals("1 giờ 20 phút", TableAge.text(Duration.ofMinutes(80)));
        assertEquals("26 giờ 5 phút", TableAge.text(Duration.ofMinutes(26 * 60 + 5)));
        assertEquals("", TableAge.text(null));
    }

    @Test
    public void theEnglishTextUsesTheSameShape() {
        I18n.setLanguage(I18n.EN);
        assertEquals("just opened", TableAge.text(Duration.ZERO));
        assertEquals("35 min", TableAge.text(Duration.ofMinutes(35)));
        assertEquals("2 h", TableAge.text(Duration.ofHours(2)));
        assertEquals("1 h 20 min", TableAge.text(Duration.ofMinutes(80)));
    }

    @Test
    public void twoHoursOrMoreIsLong() {
        assertFalse(TableAge.isLong(Duration.ofMinutes(119)));
        assertTrue(TableAge.isLong(Duration.ofHours(2)));
        assertTrue(TableAge.isLong(Duration.ofDays(1)));
        assertFalse(TableAge.isLong(null));
    }
}
