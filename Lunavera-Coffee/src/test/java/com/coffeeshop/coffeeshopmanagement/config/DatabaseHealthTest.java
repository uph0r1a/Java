package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.*;

/** TODO 4.24: the numbers and the warning of the dashboard's database health line. No database needed. */
public class DatabaseHealthTest {

    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

    private static Path file(Path dir, String name, int bytes, Instant modified) throws Exception {
        Path p = dir.resolve(name);
        Files.write(p, new byte[bytes]);
        Files.setLastModifiedTime(p, FileTime.from(modified));
        return p;
    }

    @Test
    public void noBackupOrAnOldOneIsStale() {
        assertTrue(DatabaseHealth.backupIsStale(null, NOW));
        assertTrue(DatabaseHealth.backupIsStale(NOW.minusSeconds(8 * 86_400L), NOW));
        assertFalse(DatabaseHealth.backupIsStale(NOW.minusSeconds(7 * 86_400L), NOW));
        assertFalse(DatabaseHealth.backupIsStale(NOW.minusSeconds(3600), NOW));
    }

    @Test
    public void newestBackupLooksOnlyAtRealBackups() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-health");
        file(dir, "auto-20261020-030000.zip", 10, NOW.minusSeconds(4 * 86_400L));
        file(dir, "manual.lvbak", 10, NOW.minusSeconds(2 * 86_400L));
        file(dir, "before-restore-20261023-120000.zip", 10, NOW.minusSeconds(3600)); // a safety copy: not a backup of the day
        file(dir, "auto-20261023-000000.zip.part", 10, NOW.minusSeconds(60));          // unfinished
        file(dir, "notes.txt", 10, NOW);
        assertEquals(NOW.minusSeconds(2 * 86_400L), DatabaseHealth.newestBackup(dir, NOW).orElseThrow());
    }

    @Test
    public void aFutureDatedBackupDoesNotCount() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-health");
        file(dir, "auto-20991231-000000.zip", 10, NOW.plusSeconds(400L * 86_400L));
        assertTrue(DatabaseHealth.newestBackup(dir, NOW).isEmpty());
        file(dir, "auto-20261022-000000.zip", 10, NOW.minusSeconds(86_400L));
        assertEquals(NOW.minusSeconds(86_400L), DatabaseHealth.newestBackup(dir, NOW).orElseThrow());
    }

    @Test
    public void missingFolderGivesNothing() {
        Path nowhere = Path.of("this-folder-does-not-exist-" + System.nanoTime());
        assertTrue(DatabaseHealth.newestBackup(nowhere, NOW).isEmpty());
        assertEquals(0, DatabaseHealth.folderBytes(nowhere));
    }

    @Test
    public void folderSizeAddsUpTheFiles() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-health");
        file(dir, "a.zip", 1000, NOW);
        file(dir, "b.zip", 2500, NOW);
        Files.createDirectory(dir.resolve("sub"));
        assertEquals(3500, DatabaseHealth.folderBytes(dir));
    }

    @Test
    public void textAndWarning() {
        DatabaseHealth.Health none = new DatabaseHealth.Health(2_000_000L, null, 2, 0);
        assertNotNull(DatabaseHealth.warning(none, NOW));
        String line = DatabaseHealth.describe(none, NOW, ZoneOffset.UTC);
        assertTrue(line, line.contains("v2"));

        DatabaseHealth.Health old = new DatabaseHealth.Health(null, NOW.minusSeconds(9 * 86_400L), 0, 5_000_000L);
        String warn = DatabaseHealth.warning(old, NOW);
        assertNotNull(warn);
        assertTrue(warn, warn.contains("9"));

        DatabaseHealth.Health fresh = new DatabaseHealth.Health(2_000_000L, NOW.minusSeconds(3600), 2, 1000);
        assertNull(DatabaseHealth.warning(fresh, NOW));
        assertTrue(DatabaseHealth.describe(fresh, NOW, ZoneOffset.UTC).contains("23/10/2026 23:00"));
    }

    /** TODO 4.24: a copy saved by hand outside data/backups (a USB stick) is shown from the time the app noted. */
    @Test
    public void theLineShowsTheLastCopySavedByHand() {
        DatabaseHealth.Health none = new DatabaseHealth.Health(2_000_000L, null, 2, 0);
        assertNull(none.lastManual());
        String without = DatabaseHealth.describe(none, NOW, ZoneOffset.UTC);
        assertFalse(without, without.contains("20/10/2026 08:30"));

        DatabaseHealth.Health with = new DatabaseHealth.Health(2_000_000L, null, 2, 0, Instant.parse("2026-10-20T08:30:00Z"));
        String line = DatabaseHealth.describe(with, NOW, ZoneOffset.UTC);
        assertTrue(line, line.contains("20/10/2026 08:30"));
        // the warning is about the backups folder and does not change
        assertNotNull(DatabaseHealth.warning(with, NOW));
    }
}
