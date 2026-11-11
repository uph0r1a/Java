package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.68: the books are kept in the shop's zone whatever zone the PC is set to, and the start-up says when the PC differs. The
 * PC's zone is changed with {@code TimeZone.setDefault} and put back after each test.
 */
public class ShopZoneTest {

    private static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");

    private TimeZone original;
    private LocalDate pinnedAtStart;

    @Before
    public void remember() {
        original = TimeZone.getDefault();
        // the database tests pin the date for the whole run (TestDatabaseSupport); put that back afterwards if this JVM had it
        pinnedAtStart = AppClock.today().equals(LocalDate.now()) ? null : AppClock.today();
    }

    @After
    public void restore() {
        TimeZone.setDefault(original);
        AppClock.reset();
        if (pinnedAtStart != null) AppClock.pinDate(pinnedAtStart);
    }

    private static void pcIsIn(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
    }

    private static void assertNear(LocalDateTime expected, LocalDateTime actual) {
        assertTrue("expected about " + expected + " but got " + actual, Duration.between(expected, actual).abs().toSeconds() < 5);
    }

    // ------------------------------------------------------------------ the clock

    @Test
    public void aPcLeftOnUtcStillWritesTheShopsTime() {
        pcIsIn("UTC");
        AppClock.reset();
        AppClock.setZone(HCM);
        assertNear(LocalDateTime.now(HCM), AppClock.now());
        assertTrue("seven hours from what the PC's own zone says", Duration.between(LocalDateTime.now(), AppClock.now()).toHours() >= 6);
    }

    @Test
    public void aPcOnAnotherContinentToo() {
        pcIsIn("America/Los_Angeles");
        AppClock.reset();
        AppClock.setZone(HCM);
        assertNear(LocalDateTime.now(HCM), AppClock.now());
    }

    @Test
    public void withoutAZoneItIsThePcsOwnAsBefore() {
        pcIsIn("America/Los_Angeles");
        AppClock.reset();
        assertEquals(ZoneId.systemDefault(), AppClock.zone());
        assertNear(LocalDateTime.now(), AppClock.now());
    }

    @Test
    public void resetGoesBackToThePcsZone() {
        AppClock.setZone(HCM);
        AppClock.reset();
        assertEquals(ZoneId.systemDefault(), AppClock.zone());
    }

    @Test
    public void theEveningOfOneDayInUtcIsTheNextMorningInTheShop() {
        // 18:00 UTC on 2 November is 01:00 on 3 November in Vietnam: on a PC left on UTC the evening sales used to land on the wrong day
        Instant evening = Instant.parse("2026-11-02T18:00:00Z");
        pcIsIn("UTC");
        AppClock.reset();
        AppClock.fixInstant(evening);
        assertEquals("the PC's own zone gives the old day", LocalDate.of(2026, 11, 2), AppClock.today());
        AppClock.setZone(HCM);
        assertEquals(LocalDate.of(2026, 11, 3), AppClock.today());
        assertEquals(LocalDateTime.of(2026, 11, 3, 1, 0), AppClock.now());
    }

    @Test
    public void aPinnedDateKeepsTheShopsTimeOfDay() {
        pcIsIn("UTC");
        AppClock.reset();
        AppClock.setZone(HCM);
        AppClock.pinDate(LocalDate.of(2001, 2, 3));
        assertEquals(LocalDate.of(2001, 2, 3), AppClock.today());
        assertTrue(Duration.between(LocalTime.now(HCM), AppClock.now().toLocalTime()).abs().toSeconds() < 5);
    }

    @Test
    public void theBackupReminderReadsTheSameMomentWhateverTheZones() {
        for (String pc : new String[]{"UTC", "America/Los_Angeles", "Asia/Ho_Chi_Minh"}) {
            pcIsIn(pc);
            AppClock.reset();
            AppClock.setZone(HCM);
            long gap = Math.abs(Duration.between(Instant.now(), BackupReminder.now()).toSeconds());
            assertTrue("PC in " + pc + ": " + gap + " s from the real moment", gap < 5);
        }
    }

    // ------------------------------------------------------------------ the zone to use

    @Test
    public void noSettingMeansVietnam() {
        assertEquals(HCM, ShopZone.DEFAULT);
        assertEquals(HCM, ShopZone.resolve(null));
        assertEquals(HCM, ShopZone.resolve(""));
        assertEquals(HCM, ShopZone.resolve("   "));
    }

    @Test
    public void aShopElsewhereCanNameItsZone() {
        assertEquals(ZoneId.of("Asia/Bangkok"), ShopZone.resolve(" Asia/Bangkok "));
        assertEquals(ZoneId.of("Europe/Paris"), ShopZone.resolve("Europe/Paris"));
    }

    @Test
    public void aZoneThatDoesNotExistFallsBackToVietnam() {
        assertEquals(HCM, ShopZone.resolve("Mars/Olympus_Mons"));
        assertEquals(HCM, ShopZone.resolve("not a zone!!"));
    }

    // ------------------------------------------------------------------ the warning

    private static final Instant MOMENT = Instant.parse("2026-11-02T18:00:00Z");

    @Test
    public void aDifferentOffsetIsWarnedAboutWithBothTimes() {
        String text = ShopZone.mismatchWarning(ZoneId.of("UTC"), HCM, MOMENT);
        assertTrue(text != null);
        assertTrue(text.contains("UTC+00:00"));
        assertTrue(text.contains("UTC+07:00"));
        assertTrue("the PC's time", text.contains("18:00 02/11/2026"));
        assertTrue("the shop's time", text.contains("01:00 03/11/2026"));
    }

    @Test
    public void theSameOffsetIsNotWarnedAboutEvenInAnotherZone() {
        assertNull(ShopZone.mismatchWarning(ZoneId.of("Asia/Bangkok"), HCM, MOMENT));
        assertNull(ShopZone.mismatchWarning(HCM, HCM, MOMENT));
    }

    @Test
    public void aSeasonalZoneIsWarnedAboutWhateverItsOffsetIsAtThatMoment() {
        assertTrue(ShopZone.mismatchWarning(ZoneId.of("America/Los_Angeles"), HCM, MOMENT) != null);
        assertTrue(ShopZone.mismatchWarning(ZoneId.of("Europe/London"), HCM, Instant.parse("2026-07-01T12:00:00Z")) != null);
    }

    @Test
    public void theZoneIsNamedWithItsOffset() {
        assertEquals("Asia/Ho_Chi_Minh (UTC+07:00)", ShopZone.describe(HCM, MOMENT));
        assertEquals("UTC (UTC+00:00)", ShopZone.describe(ZoneId.of("UTC"), MOMENT));
        assertNotEquals(ShopZone.describe(ZoneId.of("America/Los_Angeles"), MOMENT), ShopZone.describe(ZoneId.of("America/Los_Angeles"), Instant.parse("2026-07-01T12:00:00Z")));
    }
}
