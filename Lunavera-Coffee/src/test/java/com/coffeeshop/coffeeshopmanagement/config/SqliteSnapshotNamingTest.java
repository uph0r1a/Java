package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The hourly SQLite snapshot: file names, "keep the newest 10", and when the first one is due (no database needed). */
public class SqliteSnapshotNamingTest {

    private Path dir;

    @Before
    public void makeDir() throws IOException {
        dir = Files.createTempDirectory("lunavera-snapshot-naming");
    }

    @After
    public void clean() throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static LocalDateTime at(int hour) {
        return LocalDateTime.of(2026, 10, 13, 0, 0, 7).plusHours(hour);
    }

    private Path touch(String name) throws IOException {
        return Files.writeString(dir.resolve(name), "x");
    }

    @Test
    public void theFileNameCarriesTheTimeAndReadsBack() {
        assertEquals("lunavera-20261013-112007.db", SqliteSnapshotService.fileName(LocalDateTime.of(2026, 10, 13, 11, 20, 7)));
        assertEquals(Optional.of(LocalDateTime.of(2026, 10, 13, 11, 20, 7)),
                SqliteSnapshotService.parseStamp("lunavera-20261013-112007.db"));
    }

    @Test
    public void onlyRealSnapshotNamesAreRecognised() {
        for (String name : new String[]{"lunavera.db", "lunavera-2026.db", "lunavera-20261013-112007.db.partial",
                "lunavera-20261313-112007.db", "x-20261013-112007.db", "notes.txt", "lunavera-20261013-112007.sql"}) {
            assertEquals(name, Optional.empty(), SqliteSnapshotService.parseStamp(name));
        }
    }

    @Test
    public void onlyTheNewestTenAreKept() throws IOException {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            files.add(touch(SqliteSnapshotService.fileName(at(i))));
        }
        List<Path> toDelete = SqliteSnapshotService.filesToDelete(files, SqliteSnapshotService.KEEP);
        assertEquals(2, toDelete.size());
        assertTrue(toDelete.contains(files.get(0)));
        assertTrue(toDelete.contains(files.get(1)));
        assertFalse("the newest is never deleted", toDelete.contains(files.get(11)));
    }

    @Test
    public void nothingIsDeletedWhileThereAreTenOrFewer() throws IOException {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            files.add(touch(SqliteSnapshotService.fileName(at(i))));
        }
        assertEquals(List.of(), SqliteSnapshotService.filesToDelete(files, 10));
        assertEquals(List.of(), SqliteSnapshotService.filesToDelete(files.subList(0, 3), 10));
        assertEquals(List.of(), SqliteSnapshotService.filesToDelete(List.of(), 10));
    }

    @Test
    public void theOrderComesFromTheTimeInTheNameNotFromTheOrderOfTheList() throws IOException {
        List<Path> shuffled = new ArrayList<>();
        for (int i : new int[]{5, 0, 9, 2, 7, 1, 11, 3, 8, 4, 10, 6}) {
            shuffled.add(touch(SqliteSnapshotService.fileName(at(i))));
        }
        List<String> deleted = SqliteSnapshotService.filesToDelete(shuffled, 10).stream().map(p -> p.getFileName().toString()).toList();
        assertEquals(List.of(SqliteSnapshotService.fileName(at(1)), SqliteSnapshotService.fileName(at(0))), deleted);
    }

    @Test
    public void pruningRemovesOnlyOldSnapshotsAndLeavesEverythingElseAlone() throws IOException {
        for (int i = 0; i < 12; i++) {
            touch(SqliteSnapshotService.fileName(at(i)));
        }
        touch("lunavera.db");
        touch("notes.txt");
        touch(SqliteSnapshotService.fileName(at(20)) + ".partial");

        SqliteSnapshotService.prune(dir, 10);

        long snapshots = Files.list(dir).filter(p -> SqliteSnapshotService.parseStamp(p.getFileName().toString()).isPresent()).count();
        assertEquals(10, snapshots);
        assertFalse(Files.exists(dir.resolve(SqliteSnapshotService.fileName(at(0)))));
        assertFalse(Files.exists(dir.resolve(SqliteSnapshotService.fileName(at(1)))));
        assertTrue(Files.exists(dir.resolve(SqliteSnapshotService.fileName(at(11)))));
        assertTrue(Files.exists(dir.resolve("lunavera.db")));
        assertTrue(Files.exists(dir.resolve("notes.txt")));
        assertTrue(Files.exists(dir.resolve(SqliteSnapshotService.fileName(at(20)) + ".partial")));
    }

    @Test
    public void theNewestSnapshotTimeIsFoundAmongOtherFiles() throws IOException {
        assertEquals(Optional.empty(), SqliteSnapshotService.newestSnapshotTime(dir));
        assertEquals(Optional.empty(), SqliteSnapshotService.newestSnapshotTime(dir.resolve("missing")));
        touch(SqliteSnapshotService.fileName(at(3)));
        touch(SqliteSnapshotService.fileName(at(8)));
        touch("notes.txt");
        assertEquals(Optional.of(at(8)), SqliteSnapshotService.newestSnapshotTime(dir));
    }

    @Test
    public void theFirstSnapshotIsSoonWhenThereIsNoneOrTheNewestIsAnHourOld() {
        LocalDateTime now = at(0);
        assertEquals(30, SqliteSnapshotService.initialDelaySeconds(Optional.empty(), now));
        assertEquals(30, SqliteSnapshotService.initialDelaySeconds(Optional.of(now.minusHours(1)), now));
        assertEquals(30, SqliteSnapshotService.initialDelaySeconds(Optional.of(now.minusHours(5)), now));
    }

    @Test
    public void aFreshSnapshotDelaysTheNextOneUntilItIsDue() {
        LocalDateTime now = at(0);
        assertEquals(50 * 60, SqliteSnapshotService.initialDelaySeconds(Optional.of(now.minusMinutes(10)), now));
        assertEquals("never sooner than the start-up grace period", 30,
                SqliteSnapshotService.initialDelaySeconds(Optional.of(now.minusSeconds(3590)), now));
    }

    @Test
    public void aSnapshotDatedInTheFutureDoesNotBlockTheNextOne() {
        LocalDateTime now = at(0);
        assertEquals(30, SqliteSnapshotService.initialDelaySeconds(Optional.of(now.plusHours(3)), now));
    }

    @Test
    public void theRulesAreHourlyAndTen() {
        assertEquals(10, SqliteSnapshotService.KEEP);
        assertEquals(60, SqliteSnapshotService.INTERVAL_MINUTES);
    }
}
