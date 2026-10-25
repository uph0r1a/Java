package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

import static org.junit.Assert.*;

/** TODO 5.18: the question about a backup when the administrator leaves. */
public class BackupReminderTest {

    private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

    private String realDataDir;
    private Path sandbox;

    @Before
    public void useASandbox() throws IOException {
        realDataDir = System.getProperty("lunavera.dataDir");
        sandbox = Files.createTempDirectory("lunavera-remind-test");
        System.setProperty("lunavera.dataDir", sandbox.resolve("data").toString());
        BackupReminder.resetForTests();
    }

    @After
    public void restoreAndClean() throws IOException {
        if (realDataDir == null) System.clearProperty("lunavera.dataDir"); else System.setProperty("lunavera.dataDir", realDataDir);
        BackupReminder.resetForTests();
        try (var walk = Files.walk(sandbox)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void noBackupByHandMeansTheQuestionIsDue() {
        assertTrue(BackupReminder.due(null, NOW));
    }

    @Test
    public void aWeekOldBackupIsStillFineAndAnOlderOneIsNot() {
        assertFalse(BackupReminder.due(NOW.minus(Duration.ofDays(3)), NOW));
        assertFalse(BackupReminder.due(NOW.minus(Duration.ofDays(7)), NOW));
        assertTrue(BackupReminder.due(NOW.minus(Duration.ofDays(7)).minusSeconds(1), NOW));
    }

    @Test
    public void aBackupDatedInTheFutureCountsAsDueBecauseTheClockWasWrong() {
        assertTrue(BackupReminder.due(NOW.plus(Duration.ofDays(2)), NOW));
        assertFalse("a few minutes of clock difference is tolerated", BackupReminder.due(NOW.plusSeconds(120), NOW));
    }

    @Test
    public void daysSinceIsWholeDaysAndNeverNegative() {
        assertEquals(9, BackupReminder.daysSince(NOW.minus(Duration.ofHours(9 * 24 + 5)), NOW));
        assertEquals(0, BackupReminder.daysSince(NOW.plus(Duration.ofDays(1)), NOW));
    }

    @Test
    public void theTimeOfAHandMadeBackupIsRemembered() {
        assertNull(BackupReminder.lastManual());
        Instant when = NOW.minusSeconds(3600);
        BackupReminder.recordManual(when);
        assertEquals(when, BackupReminder.lastManual());
        assertFalse(BackupReminder.wouldAsk(NOW));
    }

    @Test
    public void aDamagedSettingIsTreatedAsNoBackup() {
        UiSettings.put(BackupReminder.KEY, "yesterday");
        assertNull(BackupReminder.lastManual());
        assertTrue(BackupReminder.wouldAsk(NOW));
    }

    @Test
    public void thePersonIsAskedOnlyOncePerRun() {
        assertTrue(BackupReminder.wouldAsk(NOW));
        assertTrue("looking does not use the question up", BackupReminder.wouldAsk(NOW));
        assertTrue(BackupReminder.shouldAskNow(NOW));
        assertFalse(BackupReminder.shouldAskNow(NOW));
        assertFalse(BackupReminder.wouldAsk(NOW));
    }

    @Test
    public void nothingIsAskedWhenABackupIsRecent() {
        BackupReminder.recordManual(NOW.minus(Duration.ofDays(1)));
        assertFalse(BackupReminder.shouldAskNow(NOW));
        assertTrue("the question was not used up", BackupReminder.due(BackupReminder.lastManual(), NOW.plus(Duration.ofDays(30))));
    }
}
