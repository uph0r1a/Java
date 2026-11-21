package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import static org.junit.Assert.*;

/**
 * TODO 6.78 (mutation sweep, profile pit-db): the edges of {@link BackupService} that decide when an automatic backup is due,
 * which one carries the pictures and which old copies are deleted: exactly one interval old, several copies of different age,
 * names that cannot be read as a time, and the lock on a backup. Plain files in temporary folders, no database.
 */
public class BackupDueAndPruneEdgesTest {

    private static Path touch(Path dir, String name, Instant modified) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, "x");
        Files.setLastModifiedTime(file, FileTime.from(modified));
        return file;
    }

    private static Instant wholeSecondNow() {
        return Instant.ofEpochSecond(Instant.now().getEpochSecond());
    }

    // ---- isDue ----

    @Test
    public void aCopyExactlyOneIntervalOldMakesTheNextBackupDue() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-due");
        Instant now = wholeSecondNow();
        touch(dir, "auto-20260101-100000.zip", now.minus(BackupService.AUTOMATIC_EVERY));
        assertTrue("one full interval has passed", BackupService.isDue(dir, now));
    }

    @Test
    public void aCopyASecondYoungerThanTheIntervalIsNotDueYet() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-due");
        Instant now = wholeSecondNow();
        touch(dir, "auto-20260101-100000.zip", now.minus(BackupService.AUTOMATIC_EVERY).plusSeconds(1));
        assertFalse(BackupService.isDue(dir, now));
    }

    @Test
    public void theNewestOfSeveralCopiesDecidesWhetherABackupIsDue() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-due");
        Instant now = wholeSecondNow();
        touch(dir, "auto-20260101-100000.zip", now.minusSeconds(30 * 3600L));
        touch(dir, "auto-20260102-100000.zip", now.minusSeconds(3600L));
        touch(dir, "auto-20260103-100000.zip", now.minusSeconds(50 * 3600L));
        assertFalse("the copy made an hour ago is the newest", BackupService.isDue(dir, now));
    }

    // ---- picturesDue: the newest copy with pictures is found by name order, not by the order the folder lists them ----

    @Test
    public void aFullCopyThatIsTheNewestMeansNoPicturesThisTime() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-pics");
        Instant now = LocalDateTime.of(2026, 3, 1, 12, 0).atZone(ZoneId.systemDefault()).toInstant();
        for (int day = 1; day <= 7; day++) {
            touch(dir, String.format(Locale.ROOT, "auto-202602%02d-100000-db.zip", day), now);
        }
        touch(dir, "auto-20260208-100000.zip", now);
        assertFalse("the newest copy has the pictures", BackupService.picturesDue(dir, now));
    }

    @Test
    public void aFullCopyThatIsSevenBackBringsThePicturesAgain() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-pics");
        Instant now = LocalDateTime.of(2026, 3, 1, 12, 0).atZone(ZoneId.systemDefault()).toInstant();
        touch(dir, "auto-20260201-100000.zip", now);
        for (int day = 2; day <= 8; day++) {
            touch(dir, String.format(Locale.ROOT, "auto-202602%02d-100000-db.zip", day), now);
        }
        assertTrue("seven copies without pictures since the last full one", BackupService.picturesDue(dir, now));
    }

    @Test
    public void aNameTooShortToHoldATimeIsNotAnErrorForThePictureRule() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-pics");
        Instant now = LocalDateTime.of(2026, 3, 1, 12, 0).atZone(ZoneId.systemDefault()).toInstant();
        touch(dir, "auto-x.zip", now);
        assertFalse("it counts as a full copy made now", BackupService.picturesDue(dir, now));
    }

    // ---- pruning: a name whose time cannot be read is not taken for a wrong date ----

    @Test
    public void aNameWithAnUnreadableTimeIsRankedByItsNameNotAsTheOldest() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-prune-name");
        Instant now = LocalDateTime.of(2026, 3, 1, 12, 0).atZone(ZoneId.systemDefault()).toInstant();
        Path dated = touch(dir, "auto-20260101-100000.zip", now);
        Path unreadable = touch(dir, "auto-zzzzzzzzzzzzzzz.zip", now);
        assertEquals(1, BackupService.pruneAutomatic(dir, 1, now));
        assertTrue("kept: its name ranks after the dated one", Files.exists(unreadable));
        assertFalse(Files.exists(dated));
    }

    @Test
    public void aNameTooShortToHoldATimeIsRankedByItsNameWhenPruning() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-prune-short");
        Instant now = LocalDateTime.of(2026, 3, 1, 12, 0).atZone(ZoneId.systemDefault()).toInstant();
        Path dated = touch(dir, "auto-20260101-100000.zip", now);
        Path shortName = touch(dir, "auto-x.zip", now);
        assertEquals(1, BackupService.pruneAutomatic(dir, 1, now));
        assertTrue(Files.exists(shortName));
        assertFalse(Files.exists(dated));
    }

    // ---- the lock on a backup ----

    @Test
    public void onlyALockedFileIsSaidToBeLocked() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-lock");
        Path plain = dir.resolve("plain.zip");
        Files.writeString(plain, "not locked at all");
        Path locked = dir.resolve("locked.zip");
        BackupCrypto.encrypt(plain, locked, "a passphrase".toCharArray());
        assertFalse(BackupService.isEncryptedBackup(plain));
        assertTrue(BackupService.isEncryptedBackup(locked));
    }
}
