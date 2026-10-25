package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/** TODO 4.35: the second place for the automatic backups. */
public class SecondBackupFolderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private String realDataDir;
    private Path data;
    private Path stick;

    /** Its own data folder (and so its own settings file) for the end-to-end tests below. */
    @Before
    public void ownDataFolder() throws IOException {
        realDataDir = System.getProperty("lunavera.dataDir");
        data = tmp.newFolder("own-data").toPath();
        Files.createDirectories(data.resolve("backups"));
        System.setProperty("lunavera.dataDir", data.toString());
        stick = tmp.newFolder("own-stick").toPath();
    }

    @After
    public void backToTheRealDataFolder() {
        if (realDataDir == null) {
            System.clearProperty("lunavera.dataDir");
        } else {
            System.setProperty("lunavera.dataDir", realDataDir);
        }
    }

    private Path file(Path dir, String name, String content) throws IOException {
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), content);
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    public void aMissingFolderIsRefused() {
        assertEquals("backup2.problem.missing", SecondBackupFolder.problem(tmp.getRoot().toPath().resolve("nope"), tmp.getRoot().toPath().resolve("data")));
        assertEquals("backup2.problem.missing", SecondBackupFolder.problem(null, tmp.getRoot().toPath()));
    }

    @Test
    public void theDataFolderAndFoldersInsideItAreRefused() throws IOException {
        Path data = tmp.newFolder("data").toPath();
        Path backups = Files.createDirectories(data.resolve("backups"));
        assertEquals("backup2.problem.insidedata", SecondBackupFolder.problem(data, data));
        assertEquals("backup2.problem.insidedata", SecondBackupFolder.problem(backups, data));
    }

    @Test
    public void aWritableFolderElsewhereIsAcceptedAndNoProbeFileStaysBehind() throws IOException {
        Path data = tmp.newFolder("data").toPath();
        Path stick = tmp.newFolder("stick").toPath();
        assertNull(SecondBackupFolder.problem(stick, data));
        assertEquals(List.of(), names(stick));
    }

    @Test
    public void aSiblingWithTheSamePrefixIsNotInsideTheDataFolder() throws IOException {
        Path data = tmp.newFolder("data").toPath();
        Path other = tmp.newFolder("data-old").toPath();
        assertNull(SecondBackupFolder.problem(other, data));
    }

    @Test
    public void copyInCopiesTheFileAndLeavesNoPartFile() throws IOException {
        Path src = file(tmp.newFolder("backups").toPath(), "auto-20261024-120000.zip", "hello backup");
        Path stick = tmp.newFolder("stick").toPath();
        Path copy = SecondBackupFolder.copyInto(src, stick);
        assertEquals("hello backup", Files.readString(copy));
        assertEquals(List.of("auto-20261024-120000.zip"), names(stick));
    }

    @Test
    public void aCopyThatIsAlreadyThereWithTheSameSizeIsNotWrittenAgain() throws IOException {
        Path src = file(tmp.newFolder("backups").toPath(), "auto-20261024-120000.zip", "same");
        Path stick = tmp.newFolder("stick").toPath();
        Path copy = SecondBackupFolder.copyInto(src, stick);
        long before = Files.getLastModifiedTime(copy).toMillis();
        Files.setLastModifiedTime(copy, java.nio.file.attribute.FileTime.fromMillis(before - 100_000));
        SecondBackupFolder.copyInto(src, stick);
        assertEquals(before - 100_000, Files.getLastModifiedTime(copy).toMillis());
    }

    @Test
    public void pruneKeepsTheNewestAutomaticCopiesAndNeverTouchesOtherFiles() throws IOException {
        Path stick = tmp.newFolder("stick").toPath();
        for (int day = 1; day <= 5; day++) {
            file(stick, "auto-2026102" + day + "-120000.zip", "x");
        }
        file(stick, "holiday-photos.zip", "mine");
        file(stick, "notes.txt", "mine");
        assertEquals(3, SecondBackupFolder.prune(stick, 2));
        assertEquals(List.of("auto-20261024-120000.zip", "auto-20261025-120000.zip", "holiday-photos.zip", "notes.txt"), names(stick));
    }

    @Test
    public void theNewestAutomaticBackupIsFoundByItsName() throws IOException {
        Path dir = tmp.newFolder("backups").toPath();
        file(dir, "auto-20261022-090000.zip", "a");
        file(dir, "auto-20261024-090000-db.zip", "b");
        file(dir, "before-restore-20261025-090000.zip", "c");
        file(dir, "manual.zip", "d");
        assertEquals("auto-20261024-090000-db.zip", SecondBackupFolder.newestAutomatic(dir).orElseThrow().getFileName().toString());
        assertTrue(SecondBackupFolder.newestAutomatic(tmp.getRoot().toPath().resolve("none")).isEmpty());
    }

    private static SecondBackupFolder.Status status(Path folder, Instant last, String error) {
        return new SecondBackupFolder.Status(folder, last, error);
    }

    @Test
    public void theStateFollowsTheThreeDayRuleAndErrors() {
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        Path folder = Path.of("E:/backups");
        assertEquals(SecondBackupFolder.State.NOT_SET, SecondBackupFolder.stateOf(status(null, null, ""), now));
        assertEquals(SecondBackupFolder.State.NEVER, SecondBackupFolder.stateOf(status(folder, null, ""), now));
        assertEquals(SecondBackupFolder.State.OK, SecondBackupFolder.stateOf(status(folder, now.minus(Duration.ofDays(3)), ""), now));
        assertEquals(SecondBackupFolder.State.OLD, SecondBackupFolder.stateOf(status(folder, now.minus(Duration.ofDays(3)).minusSeconds(1), ""), now));
        assertEquals(SecondBackupFolder.State.FAILED, SecondBackupFolder.stateOf(status(folder, now, "disk full"), now));
        assertEquals("a copy dated in the future is not fresh", SecondBackupFolder.State.NEVER,
                SecondBackupFolder.stateOf(status(folder, now.plus(Duration.ofDays(30)), ""), now));
    }

    @Test
    public void attentionIsNeededWhenOldFailedOrNeverCopied() {
        Instant now = Instant.parse("2026-10-24T12:00:00Z");
        Path folder = Path.of("E:/backups");
        assertFalse(SecondBackupFolder.needsAttention(status(null, null, ""), now));
        assertFalse(SecondBackupFolder.needsAttention(status(folder, now.minusSeconds(60), ""), now));
        assertTrue(SecondBackupFolder.needsAttention(status(folder, null, ""), now));
        assertTrue(SecondBackupFolder.needsAttention(status(folder, now.minus(Duration.ofDays(9)), ""), now));
        assertTrue(SecondBackupFolder.needsAttention(status(folder, now, "x"), now));
    }

    // ---- the age on the dashboard is the age of the BACKUP on the stick (TODO 4.35) ----

    private Path backupAged(String name, Duration age) throws IOException {
        Path backup = file(data.resolve("backups"), name, "content of " + name);
        Files.setLastModifiedTime(backup, java.nio.file.attribute.FileTime.from(Instant.now().minus(age)));
        return backup;
    }

    @Test
    public void anOldBackupOnTheStickIsShownAsOldAndNotAsToday() throws IOException {
        Path old = backupAged("auto-20261014-090000.zip", Duration.ofDays(10));
        SecondBackupFolder.configure(stick);
        assertTrue(SecondBackupFolder.catchUp());
        SecondBackupFolder.Status status = SecondBackupFolder.status();
        assertEquals(SecondBackupFolder.State.OLD, SecondBackupFolder.stateOf(status, Instant.now()));
        assertTrue(SecondBackupFolder.needsAttention(status, Instant.now()));
        assertEquals("the stored time is the backup's own", Files.getLastModifiedTime(old).toInstant(), status.lastCopy());
        assertTrue(SecondBackupFolder.describe(status, Instant.now()).contains("10"));
    }

    @Test
    public void checkingAgainDoesNotRefreshTheTimeOfAnUnchangedBackup() throws IOException {
        backupAged("auto-20261014-090000.zip", Duration.ofDays(10));
        SecondBackupFolder.configure(stick);
        SecondBackupFolder.catchUp();
        Instant first = SecondBackupFolder.status().lastCopy();
        assertFalse("nothing new to copy", SecondBackupFolder.catchUp());
        assertFalse(SecondBackupFolder.catchUp());
        assertEquals(first, SecondBackupFolder.status().lastCopy());
        assertEquals(SecondBackupFolder.State.OLD, SecondBackupFolder.stateOf(SecondBackupFolder.status(), Instant.now()));
    }

    @Test
    public void aNewBackupReachingTheStickMakesTheLineFreshAgain() throws IOException {
        backupAged("auto-20261014-090000.zip", Duration.ofDays(10));
        SecondBackupFolder.configure(stick);
        SecondBackupFolder.catchUp();
        backupAged("auto-20261024-090000.zip", Duration.ofMinutes(5));
        assertTrue(SecondBackupFolder.catchUp());
        assertEquals(SecondBackupFolder.State.OK, SecondBackupFolder.stateOf(SecondBackupFolder.status(), Instant.now()));
        assertFalse(SecondBackupFolder.needsAttention(SecondBackupFolder.status(), Instant.now()));
    }
}
