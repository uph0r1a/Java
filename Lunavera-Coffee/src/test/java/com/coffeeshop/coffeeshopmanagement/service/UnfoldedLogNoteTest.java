package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 2.85, the part that was left: when a restore keeps a write-ahead log that could not be folded in, the message at the end tells the
 * person (it used to name only the safety zip). {@link BackupService#keepUnfoldedLog} says whether it moved a log; the text exists in both
 * languages. The restore itself needs the database driver and is covered by the backup tests that run with Maven.
 */
public class UnfoldedLogNoteTest {

    private Path dir;
    private String languageAtStart;

    @Before
    public void folder() throws IOException {
        dir = Files.createTempDirectory("unfolded-log-");
        languageAtStart = I18n.current();
    }

    @After
    public void back() {
        I18n.setLanguage(languageAtStart);
    }

    @Test
    public void aLogBesideTheDatabaseIsMovedToTheBackupsFolderAndReportedAsKept() throws IOException {
        Path database = dir.resolve("lunavera.db");
        Path backups = Files.createDirectory(dir.resolve("backups"));
        Files.writeString(database, "db", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("lunavera.db-wal"), "the last, unreadable part of a save", StandardCharsets.UTF_8);

        assertTrue("a log was found and moved", BackupService.keepUnfoldedLog(database, backups));

        assertFalse("it no longer sits beside the database, where SQLite would delete it", Files.exists(dir.resolve("lunavera.db-wal")));
        try (Stream<Path> kept = Files.list(backups)) {
            Path file = kept.findFirst().orElseThrow();
            assertTrue(file.getFileName().toString().startsWith("lunavera.db-wal.unfolded-"));
            assertEquals("what was in it is kept", "the last, unreadable part of a save", Files.readString(file, StandardCharsets.UTF_8));
        }
        assertTrue("the database itself is not touched", Files.exists(database));
    }

    @Test
    public void noLogMeansNothingMovedAndNothingToReport() throws IOException {
        Path database = dir.resolve("lunavera.db");
        Path backups = Files.createDirectory(dir.resolve("backups"));
        Files.writeString(database, "db", StandardCharsets.UTF_8);
        assertFalse(BackupService.keepUnfoldedLog(database, backups));
        try (Stream<Path> kept = Files.list(backups)) {
            assertEquals(0, kept.count());
        }
    }

    @Test
    public void theNoteExistsInBothLanguagesAndNamesTheFile() {
        for (String language : new String[]{I18n.VI, I18n.EN}) {
            String text = I18n.in(language, "backup.restored.unfolded");
            assertFalse(language + " has the text", text.startsWith("backup.restored.unfolded") || text.isBlank());
            assertTrue(language + " names the file the person will look for", text.contains("lunavera.db-wal.unfolded-"));
        }
    }
}
