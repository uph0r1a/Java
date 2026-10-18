package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 3.26: a zero-byte database next to backups is damaged; a zero-byte file with nothing to lose starts a new shop. */
public class DatabaseFileCheckTest {

    private Path folder() throws IOException {
        return Files.createTempDirectory("lunavera-dbcheck");
    }

    private Path emptyDb(Path folder) throws IOException {
        return Files.createFile(folder.resolve("lunavera.db"));
    }

    @Test
    public void aMissingFileIsANewShop() throws IOException {
        assertNull(DatabaseFileCheck.problem(folder().resolve("lunavera.db")));
        assertNull(DatabaseFileCheck.problem(null));
    }

    @Test
    public void aFileWithContentIsLeftToTheMigrations() throws IOException {
        Path folder = folder();
        Files.writeString(folder.resolve("lunavera.db"), "SQLite format 3");
        Files.createDirectories(folder.resolve("backups"));
        Files.writeString(folder.resolve("backups").resolve("auto-20260101-120000.zip"), "x");
        assertNull(DatabaseFileCheck.problem(folder.resolve("lunavera.db")));
    }

    @Test
    public void aZeroByteFileNextToABackupIsDamaged() throws IOException {
        Path folder = folder();
        Path db = emptyDb(folder);
        Files.createDirectories(folder.resolve("backups"));
        Files.writeString(folder.resolve("backups").resolve("auto-20260101-120000.zip"), "x");
        String message = DatabaseFileCheck.problem(db);
        assertNotNull(message);
        assertTrue(message, message.contains("backups"));
        assertTrue(message, message.contains("0 byte"));
    }

    @Test
    public void aZeroByteFileNextToAShopPictureIsDamaged() throws IOException {
        Path folder = folder();
        Path db = emptyDb(folder);
        Files.createDirectories(folder.resolve("images"));
        Files.writeString(folder.resolve("images").resolve("12_latte.png"), "x");
        assertNotNull(DatabaseFileCheck.problem(db));
    }

    @Test
    public void aZeroByteFileWithNothingToLoseStartsANewShop() throws IOException {
        Path folder = folder();
        Path db = emptyDb(folder);
        assertNull("no backups, no pictures", DatabaseFileCheck.problem(db));
        Files.createDirectories(folder.resolve("images"));
        Files.writeString(folder.resolve("images").resolve("seed_latte.png"), "x");
        Files.createDirectories(folder.resolve("backups"));
        assertNull("only the bundled starter pictures and an empty backups folder", DatabaseFileCheck.problem(db));
    }
}
