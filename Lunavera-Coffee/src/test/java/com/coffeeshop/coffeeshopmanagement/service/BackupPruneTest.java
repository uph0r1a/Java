package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pruning the before-restore safety copies (TODO 4.28). Plain files in a temporary folder, no database. */
public class BackupPruneTest {

    private static Set<String> names(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            Set<String> found = new HashSet<>();
            files.forEach(p -> found.add(p.getFileName().toString()));
            return found;
        }
    }

    @Test
    public void keepsTheNewestBeforeRestoreCopiesAndTouchesNothingElse() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-prune");
        for (int day = 1; day <= 6; day++) {
            Files.writeString(dir.resolve(String.format(java.util.Locale.ROOT, "before-restore-202601%02d-120000.zip", day)), "safety");
        }
        Files.writeString(dir.resolve("auto-20260101-120000.zip"), "auto");
        Files.writeString(dir.resolve("lunavera-backup-manual.zip"), "mine");
        Files.writeString(dir.resolve("before-money-migration.db"), "old db");

        assertEquals(3, BackupService.pruneBeforeRestore(dir, 3));

        Set<String> left = names(dir);
        assertEquals(3 + 3, left.size());
        assertTrue(left.contains("before-restore-20260106-120000.zip"));
        assertTrue(left.contains("before-restore-20260104-120000.zip"));
        assertFalse(left.contains("before-restore-20260103-120000.zip"));
        assertTrue(left.contains("auto-20260101-120000.zip"));
        assertTrue(left.contains("lunavera-backup-manual.zip"));
        assertTrue(left.contains("before-money-migration.db"));
    }

    @Test
    public void doesNothingWhenThereAreFewCopiesOrNoFolder() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-prune");
        Files.writeString(dir.resolve("before-restore-20260101-120000.zip"), "safety");
        assertEquals(0, BackupService.pruneBeforeRestore(dir, 3));
        assertEquals(1, names(dir).size());
        assertEquals(0, BackupService.pruneBeforeRestore(dir.resolve("missing"), 3));
    }

    @Test
    public void automaticPruningStillOnlyTouchesAutomaticBackups() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-prune");
        for (int day = 1; day <= 5; day++) {
            Files.writeString(dir.resolve(String.format(java.util.Locale.ROOT, "auto-202601%02d-120000.zip", day)), "a");
            Files.writeString(dir.resolve(String.format(java.util.Locale.ROOT, "before-restore-202601%02d-120000.zip", day)), "b");
        }
        assertEquals(3, BackupService.pruneAutomatic(dir, 2));
        assertEquals(2 + 5, names(dir).size());
    }

    /** TODO 3.65: a copy named with a date from a wrong clock is deleted first, not kept while the real recent ones go. */
    @Test
    public void aWrongDatedBackupIsPrunedBeforeTheRealOnes() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-prune");
        for (int day = 1; day <= 4; day++) {
            Files.writeString(dir.resolve(String.format(java.util.Locale.ROOT, "auto-202601%02d-120000.zip", day)), "real");
        }
        Files.writeString(dir.resolve("auto-20990101-000000.zip"), "wrong clock");
        assertEquals(1, BackupService.pruneAutomatic(dir, 4, java.time.Instant.parse("2026-10-24T00:00:00Z")));
        Set<String> left = names(dir);
        assertFalse(left.contains("auto-20990101-000000.zip"));
        assertEquals(4, left.size());
        assertTrue(left.contains("auto-20260101-120000.zip"));
    }

    @Test
    public void pruningDoesNotTreatTodaysBackupAsWrongDated() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-prune");
        Files.writeString(dir.resolve("auto-20260101-120000.zip"), "old");
        Files.writeString(dir.resolve("auto-20261023-120000.zip"), "today");
        assertEquals(1, BackupService.pruneAutomatic(dir, 1, java.time.Instant.parse("2026-10-24T12:00:00Z")));
        assertTrue(names(dir).contains("auto-20261023-120000.zip"));
    }

    private static void touch(Path dir, String name) throws Exception {
        Files.writeString(dir.resolve(name), "x");
    }

    @Test
    public void picturesGoIntoTheFirstAutomaticBackupAndThenEverySeventh() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-pics");
        java.time.Instant now = java.time.LocalDateTime.of(2026, 3, 1, 12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant();
        assertTrue("nothing yet: full", BackupService.picturesDue(dir, now));
        touch(dir, "auto-20260201-100000.zip");
        for (int day = 2; day <= 6; day++) {
            assertFalse("backup " + day + " of the round has no pictures", BackupService.picturesDue(dir, now));
            touch(dir, String.format(java.util.Locale.ROOT, "auto-202602%02d-100000-db.zip", day));
        }
        assertFalse("the sixth after the full one still has none", BackupService.picturesDue(dir, now));
        touch(dir, "auto-20260207-100000-db.zip");
        assertTrue("the seventh is full again", BackupService.picturesDue(dir, now));
    }

    @Test
    public void aBackupWithAWrongFutureDateDoesNotCountForThePictureRule() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-pics");
        java.time.Instant now = java.time.LocalDateTime.of(2026, 3, 1, 12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant();
        touch(dir, "auto-20300101-100000.zip");        // made while the clock was years ahead
        assertTrue("it is ignored, so a full one is due", BackupService.picturesDue(dir, now));
    }

    @Test
    public void onlyAZipThatSaysSoIsTakenToHaveNoPictures() throws Exception {
        Path dir = Files.createTempDirectory("lunavera-pics");
        Path without = dir.resolve("without.zip");
        Path withInfoOnly = dir.resolve("old.zip");
        Path noInfo = dir.resolve("noinfo.zip");
        write(without, "Lunavera Coffee backup\n" + BackupService.NO_PICTURES_LINE + "\n");
        write(withInfoOnly, "Lunavera Coffee backup\ncreated: 2026-01-01T10:00\n");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(noInfo))) {
            z.putNextEntry(new java.util.zip.ZipEntry("lunavera.db"));
            z.write(1);
            z.closeEntry();
        }
        assertFalse(BackupService.picturesIncluded(without));
        assertTrue("an older backup without the line is a full one", BackupService.picturesIncluded(withInfoOnly));
        assertTrue(BackupService.picturesIncluded(noInfo));
        assertTrue(BackupService.picturesIncluded(dir.resolve("missing.zip")));
    }

    private static void write(Path zip, String info) throws Exception {
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            z.putNextEntry(new java.util.zip.ZipEntry("backup-info.txt"));
            z.write(info.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            z.closeEntry();
        }
    }
}

