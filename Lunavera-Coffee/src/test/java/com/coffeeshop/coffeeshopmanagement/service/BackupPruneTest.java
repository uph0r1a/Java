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
            Files.writeString(dir.resolve(String.format("before-restore-202601%02d-120000.zip", day)), "safety");
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
            Files.writeString(dir.resolve(String.format("auto-202601%02d-120000.zip", day)), "a");
            Files.writeString(dir.resolve(String.format("before-restore-202601%02d-120000.zip", day)), "b");
        }
        assertEquals(3, BackupService.pruneAutomatic(dir, 2));
        assertEquals(2 + 5, names(dir).size());
    }
}
