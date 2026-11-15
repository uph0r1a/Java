package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.70: the shop's data folder may be named with Vietnamese letters (moved under a Windows user such as "Nguyễn Văn An", a USB
 * stick named in Vietnamese). Every test class that makes a folder names it in plain ASCII, so this one runs the file side of the
 * backup in a folder called {@code Quán Cà Phê dữ liệu}: the program's own folders, a temporary file, a picture copy with accented
 * picture names, and the removal of a folder. (The database dump itself is in {@code MariaDbBackupFolderTest}; the SQLite backup and restore in such a
 * folder is {@code AccentedDataFolderSqliteTest}; the same under a real Windows account with such a name is the check in section 0 of the TODO list.) Skipped when the JVM
 * cannot make such a file name (a plain "C" locale); run with a UTF-8 locale.
 */
public class AccentedDataFolderTest {

    private static final String FOLDER = "Quán Cà Phê dữ liệu";

    private String realDataDir;
    private Path data;

    @Before
    public void pointTheDataFolderAtAnAccentedName() throws IOException {
        realDataDir = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        Path base = Files.createTempDirectory("accented-base");
        try {
            data = Files.createDirectories(base.resolve(FOLDER));
        } catch (InvalidPathException | IOException cannotNameIt) {
            Assume.assumeTrue("this JVM cannot make a file name with Vietnamese letters (use a UTF-8 locale)", false);
        }
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, data.toString());
    }

    @After
    public void restoreTheDataFolder() {
        if (realDataDir == null) System.clearProperty(DataPaths.DATA_DIR_PROPERTY);
        else System.setProperty(DataPaths.DATA_DIR_PROPERTY, realDataDir);
    }

    @Test
    public void theProgramsFoldersAndTemporaryFilesWorkInsideTheAccentedFolder() throws Exception {
        assertEquals(FOLDER, DataPaths.dataDir().getFileName().toString());
        Path tmp = DataPaths.createTempFile("lunavera-restore-sql", ".sql");
        Files.writeString(tmp, "-- Cà phê sữa đá\n", StandardCharsets.UTF_8);
        assertTrue(DataPaths.isInsideDataDir(tmp));
        assertEquals("-- Cà phê sữa đá\n", Files.readString(tmp, StandardCharsets.UTF_8));
        Files.createDirectories(DataPaths.backupsDir());
        assertTrue(Files.isDirectory(DataPaths.backupsDir()));
        assertTrue(DataPaths.backupsDir().toString().contains(FOLDER));
    }

    @Test
    public void picturesWithAccentedNamesAreCopiedOutOfABackupIntoTheAccentedFolder() throws Exception {
        Path zip = data.resolve("Sao lưu.zip");
        byte[] picture = {1, 2, 3, 4, 5};
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("images/ảnh cà phê sữa.png"));
            out.write(picture);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("images/latte.png"));
            out.write(new byte[] {9});
            out.closeEntry();
        }
        Path target = data.resolve("images.restoring");
        BackupService.extractImages(zip, target);
        assertTrue(Files.isRegularFile(target.resolve("ảnh cà phê sữa.png")));
        assertEquals(java.util.Arrays.toString(picture), java.util.Arrays.toString(Files.readAllBytes(target.resolve("ảnh cà phê sữa.png"))));
        assertTrue(Files.isRegularFile(target.resolve("latte.png")));
    }

    @Test
    public void aFolderInsideTheAccentedOneIsRemovedWithEverythingInIt() throws Exception {
        Path old = Files.createDirectories(data.resolve("images.old").resolve("thư mục con"));
        Files.writeString(old.resolve("tệp.txt"), "x");
        BackupService.deleteTree(data.resolve("images.old"));
        assertFalse(Files.exists(data.resolve("images.old")));
    }
}
