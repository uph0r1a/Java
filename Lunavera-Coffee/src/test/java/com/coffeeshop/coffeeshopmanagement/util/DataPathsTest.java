package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Where the data lives, and the one-time copy from the old ~/.lunavera-coffee folder. */
public class DataPathsTest {

    private String realHome;
    private String realDataDir;
    private Path sandbox;
    private Path home;
    private Path data;

    @Before
    public void useASandbox() throws IOException {
        realHome = System.getProperty("user.home");
        realDataDir = System.getProperty("lunavera.dataDir");
        sandbox = Files.createTempDirectory("lunavera-datapaths-test");
        home = Files.createDirectories(sandbox.resolve("home"));
        data = sandbox.resolve("project").resolve("data"); // deliberately not created yet
        System.setProperty("user.home", home.toString());
        System.setProperty("lunavera.dataDir", data.toString());
    }

    @After
    public void restoreAndClean() throws IOException {
        System.setProperty("user.home", realHome);
        if (realDataDir == null) {
            System.clearProperty("lunavera.dataDir");
        } else {
            System.setProperty("lunavera.dataDir", realDataDir);
        }
        try (var walk = Files.walk(sandbox)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private Path legacyWithData() throws IOException {
        Path legacy = Files.createDirectories(home.resolve(".lunavera-coffee"));
        Files.writeString(legacy.resolve("lunavera.db"), "old database bytes");
        Files.createDirectories(legacy.resolve("images"));
        Files.writeString(legacy.resolve("images").resolve("1_latte.png"), "old picture");
        Files.createDirectories(legacy.resolve("backups"));
        Files.writeString(legacy.resolve("backups").resolve("before-cleanup-1.db"), "old backup");
        Files.createDirectories(legacy.resolve("logs"));
        Files.writeString(legacy.resolve("logs").resolve("app-0-0.log"), "old log");
        return legacy;
    }

    @Test
    public void everyFolderLivesUnderTheConfiguredDataFolder() {
        assertEquals(data, DataPaths.dataDir());
        assertEquals(data.resolve("lunavera.db"), DataPaths.databaseFile());
        assertEquals(data.resolve("images"), DataPaths.imagesDir());
        assertEquals(data.resolve("logs"), DataPaths.logsDir());
        assertEquals(data.resolve("backups"), DataPaths.backupsDir());
    }

    @Test
    public void aRelativeSettingIsTakenFromTheStartUpFolder() {
        System.setProperty("lunavera.dataDir", "somewhere-else");
        Path expected = Path.of(System.getProperty("user.dir"), "somewhere-else").toAbsolutePath().normalize();
        assertEquals(expected, DataPaths.dataDir());
        assertTrue(DataPaths.dataDir().isAbsolute());
    }

    @Test
    public void withoutASettingTheDefaultIsDataInsideTheStartUpFolder() {
        System.clearProperty("lunavera.dataDir");
        if (System.getenv(DataPaths.DATA_DIR_ENV) != null && !System.getenv(DataPaths.DATA_DIR_ENV).isBlank()) {
            return; // this machine sets the environment variable; the default is not what applies here
        }
        assertEquals(Path.of(System.getProperty("user.dir"), "data").toAbsolutePath().normalize(), DataPaths.dataDir());
    }

    @Test
    public void aBlankSettingIsTreatedAsNoSetting() {
        System.setProperty("lunavera.dataDir", "   ");
        if (System.getenv(DataPaths.DATA_DIR_ENV) != null && !System.getenv(DataPaths.DATA_DIR_ENV).isBlank()) {
            return;
        }
        assertEquals(Path.of(System.getProperty("user.dir"), "data").toAbsolutePath().normalize(), DataPaths.dataDir());
    }

    @Test
    public void insideDataDirRecognisesTheFolderItsContentsAndOnlyThose() {
        assertTrue(DataPaths.isInsideDataDir(data));
        assertTrue(DataPaths.isInsideDataDir(data.resolve("backups").resolve("x.db")));
        assertTrue(DataPaths.isInsideDataDir(data.resolve("..").resolve("data").resolve("x.db")));
        assertFalse(DataPaths.isInsideDataDir(data.resolveSibling("data-old").resolve("x.db")));
        assertFalse(DataPaths.isInsideDataDir(home.resolve("backup.db")));
        assertFalse(DataPaths.isInsideDataDir(null));
    }

    @Test
    public void copiesTheOldDatabasePicturesAndBackupsAndLeavesTheOldFolderAlone() throws IOException {
        Path legacy = legacyWithData();

        assertTrue(DataPaths.migrateLegacyDataIfNeeded());

        assertEquals("old database bytes", Files.readString(data.resolve("lunavera.db")));
        assertEquals("old picture", Files.readString(data.resolve("images").resolve("1_latte.png")));
        assertEquals("old backup", Files.readString(data.resolve("backups").resolve("before-cleanup-1.db")));
        assertFalse("logs are history, not data: not copied", Files.exists(data.resolve("logs")));
        assertTrue("a marker records the move", Files.exists(data.resolve(".migrated-from-home")));
        assertFalse("no half-copied temp file is left", Files.exists(data.resolve("lunavera.db.copying")));
        // the old folder is untouched
        assertEquals("old database bytes", Files.readString(legacy.resolve("lunavera.db")));
        assertEquals("old picture", Files.readString(legacy.resolve("images").resolve("1_latte.png")));
        assertEquals("old log", Files.readString(legacy.resolve("logs").resolve("app-0-0.log")));
    }

    @Test
    public void runsOnlyOnce() throws IOException {
        legacyWithData();
        assertTrue(DataPaths.migrateLegacyDataIfNeeded());
        Files.writeString(data.resolve("lunavera.db"), "changed since");

        assertFalse(DataPaths.migrateLegacyDataIfNeeded());
        assertEquals("changed since", Files.readString(data.resolve("lunavera.db")));
    }

    @Test
    public void deletingTheNewDatabaseDoesNotBringTheOldOneBack() throws IOException {
        legacyWithData();
        DataPaths.migrateLegacyDataIfNeeded();
        Files.delete(data.resolve("lunavera.db"));

        assertFalse(DataPaths.migrateLegacyDataIfNeeded());
        assertFalse(Files.exists(data.resolve("lunavera.db")));
    }

    @Test
    public void neverOverwritesADatabaseThatIsAlreadyInTheNewFolder() throws IOException {
        legacyWithData();
        Files.createDirectories(data);
        Files.writeString(data.resolve("lunavera.db"), "new database");

        assertFalse(DataPaths.migrateLegacyDataIfNeeded());
        assertEquals("new database", Files.readString(data.resolve("lunavera.db")));
        assertFalse(Files.exists(data.resolve("images")));
    }

    @Test
    public void doesNothingWhenThereIsNoOldData() {
        assertFalse(DataPaths.migrateLegacyDataIfNeeded());
        assertFalse("the data folder is not even created", Files.exists(data));
    }

    @Test
    public void existingPicturesInTheNewFolderAreNotReplaced() throws IOException {
        legacyWithData();
        Files.createDirectories(data.resolve("images"));
        Files.writeString(data.resolve("images").resolve("1_latte.png"), "newer picture");

        DataPaths.migrateLegacyDataIfNeeded();

        assertEquals("newer picture", Files.readString(data.resolve("images").resolve("1_latte.png")));
    }

    @Test
    public void doesNothingWhenTheNewFolderIsTheOldFolder() throws IOException {
        Path legacy = legacyWithData();
        System.setProperty("lunavera.dataDir", legacy.toString());

        assertFalse(DataPaths.migrateLegacyDataIfNeeded());
        assertFalse(Files.exists(legacy.resolve(".migrated-from-home")));
    }

    // --- owner-only data folder (TODO 2A.4, 2A.14) ---

    private static boolean posix() {
        return java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    @Test
    public void dataFolderBecomesOwnerOnlyWhereTheSystemSupportsIt() throws IOException {
        Path folder = Files.createDirectories(data);
        Path inside = Files.createFile(folder.resolve("lunavera.db"));
        if (posix()) {
            Files.setPosixFilePermissions(folder, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
            Files.setPosixFilePermissions(inside, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
            assertTrue(DataPaths.restrictToOwner(folder));
            assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(folder)));
            // only the folder is changed: the files in it keep their own rights (the closed folder already protects them)
            assertEquals("rw-r--r--", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(inside)));
            assertTrue("a second call changes nothing and still says yes", DataPaths.restrictToOwner(folder));
        } else if (Files.getFileAttributeView(folder, java.nio.file.attribute.AclFileAttributeView.class) != null) {
            // Windows (TODO 2A.14): one owner entry on the folder and on what is inside
            assertTrue(DataPaths.restrictToOwner(folder));
            java.nio.file.attribute.AclFileAttributeView view =
                    Files.getFileAttributeView(inside, java.nio.file.attribute.AclFileAttributeView.class);
            assertEquals(1, view.getAcl().size());
            assertEquals(Files.getOwner(folder), view.getAcl().get(0).principal());
        } else {
            assertFalse("no POSIX or ACL rights on this system: nothing is changed", DataPaths.restrictToOwner(folder));
        }
    }

    @Test
    public void theWindowsAccessListHasOneOwnerEntryPassedOnToEverythingInside() {
        java.nio.file.attribute.UserPrincipal owner = () -> "shop-owner";
        java.util.List<java.nio.file.attribute.AclEntry> acl = DataPaths.ownerOnlyAcl(owner);
        assertEquals(1, acl.size());
        java.nio.file.attribute.AclEntry entry = acl.get(0);
        assertEquals(java.nio.file.attribute.AclEntryType.ALLOW, entry.type());
        assertEquals(owner, entry.principal());
        assertEquals(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class), entry.permissions());
        assertTrue(entry.flags().contains(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT));
        assertTrue(entry.flags().contains(java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT));
    }

    @Test
    public void restrictingNothingOrAFileIsHarmless() throws IOException {
        Path file = Files.createFile(Files.createDirectories(data).resolve("lunavera.db"));
        assertFalse(DataPaths.restrictToOwner(null));
        assertFalse(DataPaths.restrictToOwner(data.resolve("missing")));
        assertFalse(DataPaths.restrictToOwner(file));
    }

    // --- temporary copies stay in data/tmp (TODO 2A.21) ---

    @Test
    public void temporaryCopiesAreMadeInsideTheDataFolderAndCleanedAtStartUp() throws IOException {
        Path temp = DataPaths.createTempFile("lunavera-backup", ".db");
        assertTrue("inside data/tmp, not the system temp folder", temp.startsWith(DataPaths.tmpDir()));
        assertTrue(DataPaths.isInsideDataDir(temp));
        Path other = DataPaths.createTempFile("lunavera-inspect", ".db");
        assertEquals(2, DataPaths.cleanTmp());
        assertFalse(Files.exists(temp));
        assertFalse(Files.exists(other));
        assertEquals("nothing left to clean", 0, DataPaths.cleanTmp());
    }

    @Test
    public void cleaningWhenThereIsNoTmpFolderIsHarmless() {
        assertEquals(0, DataPaths.cleanTmp());
    }
}
