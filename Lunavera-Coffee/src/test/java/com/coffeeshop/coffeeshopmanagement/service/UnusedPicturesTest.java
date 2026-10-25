package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.UnusedPictures.Report;
import com.coffeeshop.coffeeshopmanagement.service.UnusedPictures.Result;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/** TODO 4.27: which pictures no product uses, and that only those are deleted. Plain files; no database. */
public class UnusedPicturesTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @After
    public void signOut() {
        TestSession.clear();
    }

    private static Path picture(Path dir, String name, int bytes, Duration age) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[bytes]);
        Files.setLastModifiedTime(file, FileTime.from(NOW.minus(age)));
        return file;
    }

    private static Set<String> names(Report report) {
        return report.unused().stream().map(p -> p.file().getFileName().toString()).collect(Collectors.toSet());
    }

    @Test
    public void listsOnlyThePicturesNoProductPointsTo() throws Exception {
        Path dir = Files.createTempDirectory("pics");
        picture(dir, "used.png", 100, Duration.ofDays(3));
        picture(dir, "orphan1.jpg", 1000, Duration.ofDays(3));
        picture(dir, "orphan2.png", 500, Duration.ofDays(9));
        Report report = UnusedPictures.scan(dir, List.of("used.png", " ", ""), NOW);
        assertEquals(Set.of("orphan1.jpg", "orphan2.png"), names(report));
        assertEquals(1500, report.totalBytes());
        assertFalse(report.isEmpty());
    }

    @Test
    public void aRecentPictureAndANonPictureFileAreLeftAlone() throws Exception {
        Path dir = Files.createTempDirectory("pics");
        picture(dir, "justchosen.png", 100, Duration.ofMinutes(5));   // may belong to a window still open
        picture(dir, "notes.txt", 100, Duration.ofDays(30));
        picture(dir, "Thumbs.db", 100, Duration.ofDays(30));
        picture(dir, "old.jpg", 100, Duration.ofDays(30));
        assertEquals(Set.of("old.jpg"), names(UnusedPictures.scan(dir, List.of(), NOW)));
    }

    @Test
    public void anOldFullPathStoredInTheDatabaseStillCountsAsInUse() throws Exception {
        Path dir = Files.createTempDirectory("pics");
        Path inSub = picture(dir, "sub/kept.png", 100, Duration.ofDays(3));
        picture(dir, "other.png", 100, Duration.ofDays(3));
        Report report = UnusedPictures.scan(dir, List.of(inSub.toAbsolutePath().toString()), NOW);
        assertEquals(Set.of("other.png"), names(report));
    }

    @Test
    public void aMissingFolderOrAnInvalidStoredValueGivesAnEmptyReportNotAnError() throws Exception {
        Path dir = Files.createTempDirectory("pics");
        assertTrue(UnusedPictures.scan(dir.resolve("missing"), List.of("x.png"), NOW).isEmpty());
        picture(dir, "a.png", 10, Duration.ofDays(2));
        assertEquals(Set.of("a.png"), names(UnusedPictures.scan(dir, List.of("bad\u0000name.png"), NOW)));
    }

    @Test
    public void deleteRemovesOnlyConfirmedFilesThatAreStillUnused() throws Exception {
        Path dir = Files.createTempDirectory("pics");
        Path stillUnused = picture(dir, "gone.png", 700, Duration.ofDays(3));
        Path usedMeanwhile = picture(dir, "saved-since.png", 300, Duration.ofDays(3));
        Path notConfirmed = picture(dir, "new-orphan.png", 200, Duration.ofDays(3));
        Report confirmed = UnusedPictures.scan(dir, List.of(), NOW);
        confirmed = new Report(confirmed.unused().stream().filter(p -> !p.file().equals(notConfirmed)).toList(), 1000);
        // after the list was shown, a product was saved with one of the pictures
        Report fresh = UnusedPictures.scan(dir, List.of("saved-since.png"), NOW);

        Result result = UnusedPictures.deleteConfirmed(dir, confirmed, fresh);

        assertEquals(1, result.deleted());
        assertEquals(700, result.bytesFreed());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(stillUnused));
        assertTrue("a picture a product uses now stays", Files.exists(usedMeanwhile));
        assertTrue("a picture that was not on the confirmed list stays", Files.exists(notConfirmed));
    }

    @Test
    public void onlyAnAdministratorMayDelete() throws Exception {
        TestSession.clear();
        try {
            UnusedPictures.deleteConfirmed(new Report(List.of(), 0));
            fail("an employee must not delete pictures");
        } catch (com.coffeeshop.coffeeshopmanagement.util.Permissions.PermissionDeniedException expected) {
            // good
        }
    }
}
