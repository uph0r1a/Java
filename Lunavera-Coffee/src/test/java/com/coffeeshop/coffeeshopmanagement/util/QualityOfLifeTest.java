package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** Section 5: search without accents (5.14), date presets (5.2), order number (5.12) and remembered choices (5.9). */
public class QualityOfLifeTest {

    @Test
    public void searchKeyIgnoresAccentsCaseAndExtraSpaces() {
        assertEquals("ca phe sua da", TextNormalizer.searchKey("  Cà   Phê Sữa Đá "));
        assertEquals("do van", TextNormalizer.searchKey("Đỗ Văn"));
        assertTrue(TextNormalizer.searchKey("Cà phê muối").contains(TextNormalizer.searchKey("ca phe")));
        assertTrue("typing the accents still matches", TextNormalizer.searchKey("Cà phê muối").contains(TextNormalizer.searchKey("phê")));
        // the decomposed way of typing "à" gives the same key
        assertEquals(TextNormalizer.searchKey("Cà"), TextNormalizer.searchKey("Cà"));
        assertEquals("", TextNormalizer.searchKey(null));
    }

    @Test
    public void presetsCoverTodayThisWeekFromMondayAndThisMonth() {
        LocalDate wednesday = LocalDate.of(2026, 10, 14);
        assertArrayEquals(new LocalDate[]{wednesday, wednesday}, DatePresets.Preset.TODAY.range(wednesday));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 10, 12), wednesday}, DatePresets.Preset.THIS_WEEK.range(wednesday));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 10, 1), wednesday}, DatePresets.Preset.THIS_MONTH.range(wednesday));
        LocalDate monday = LocalDate.of(2026, 10, 12);
        assertEquals("a Monday starts its own week", monday, DatePresets.Preset.THIS_WEEK.range(monday)[0]);
        LocalDate sunday = LocalDate.of(2026, 10, 18);
        assertEquals(monday, DatePresets.Preset.THIS_WEEK.range(sunday)[0]);
    }

    @Test
    public void orderNumbersAreWrittenOneWay() {
        assertEquals("#12", OrderNumber.format(12));
    }

    @Test
    public void rememberedChoicesSurviveAndBadFilesAreIgnored() throws IOException {
        Path dir = Files.createTempDirectory("lunavera-ui");
        String old = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, dir.toString());
        try {
            assertEquals("fallback", UiSettings.get("history.status", "fallback"));
            UiSettings.put("history.status", "Đã hủy");
            UiSettings.putBoolean("pos.bestSellersFirst", false);
            assertEquals("Đã hủy", UiSettings.get("history.status", "x"));
            assertFalse(UiSettings.getBoolean("pos.bestSellersFirst", true));
            assertTrue(Files.isRegularFile(dir.resolve(UiSettings.FILE_NAME)));
            // another data folder has its own settings and an unreadable number gives the fallback
            Path other = Files.createTempDirectory("lunavera-ui2");
            System.setProperty(DataPaths.DATA_DIR_PROPERTY, other.toString());
            assertEquals("x", UiSettings.get("history.status", "x"));
            UiSettings.put("w", "abc");
            assertEquals(5.0, UiSettings.getDouble("w", 5.0), 0.0);
        } finally {
            if (old == null) System.clearProperty(DataPaths.DATA_DIR_PROPERTY); else System.setProperty(DataPaths.DATA_DIR_PROPERTY, old);
        }
    }
}
