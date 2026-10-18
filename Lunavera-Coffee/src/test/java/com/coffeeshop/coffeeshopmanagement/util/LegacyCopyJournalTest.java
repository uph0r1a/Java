package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** TODO 3.40: the old data is not copied while the old app still has it open. */
public class LegacyCopyJournalTest {
    private String oldHome;
    private String oldDir;
    private Path home;
    private Path target;

    @Before
    public void setUp() throws IOException {
        oldHome = System.getProperty("user.home");
        oldDir = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        home = Files.createTempDirectory("lunavera-home");
        target = Files.createTempDirectory("lunavera-target").resolve("data");
        System.setProperty("user.home", home.toString());
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, target.toString());
        Files.createDirectories(DataPaths.legacyDir());
        Files.writeString(DataPaths.legacyDir().resolve(DataPaths.DATABASE_FILE_NAME), "old database");
    }

    @After
    public void tearDown() {
        System.setProperty("user.home", oldHome);
        if (oldDir == null) System.clearProperty(DataPaths.DATA_DIR_PROPERTY);
        else System.setProperty(DataPaths.DATA_DIR_PROPERTY, oldDir);
    }

    @Test
    public void aJournalMeansTheOldAppIsOpenSoNothingIsCopied() throws IOException {
        Files.writeString(DataPaths.legacyDir().resolve(DataPaths.DATABASE_FILE_NAME + "-journal"), "x");
        try {
            DataPaths.migrateLegacyDataIfNeeded();
            fail("copying must be refused");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("đóng"));
        }
        assertFalse(Files.exists(target.resolve(DataPaths.DATABASE_FILE_NAME)));
    }

    @Test
    public void withoutAJournalTheDatabaseIsCopied() {
        assertTrue(DataPaths.migrateLegacyDataIfNeeded());
        assertTrue(Files.exists(target.resolve(DataPaths.DATABASE_FILE_NAME)));
    }
}
