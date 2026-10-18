package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 6.42: the data folder is resolved on use, not when the class loads. A failure is reported to the caller and the next
 * call tries again; and {@link DatabaseConfig#reset()} lets the folder change after first use. The test puts everything back
 * (the property, the resolved folder, the initialised database) so the other tests in the run are not affected.
 */
public class DatabaseConfigResolveTest {

    private static final String DIR_PROPERTY = "lunavera.dataDir";

    @Test
    public void aFolderThatCannotBeCreatedIsReportedAndDoesNotPoisonTheNextCall() throws IOException {
        TestDatabaseSupport.ensureReady();
        TestDatabaseSupport.requireSqlite();
        String original = System.getProperty(DIR_PROPERTY);
        Path blocker = Files.createTempFile("lunavera-not-a-folder", ".txt");
        try {
            DatabaseConfig.reset();
            // A folder cannot be created below a regular file.
            System.setProperty(DIR_PROPERTY, blocker.resolve("data").toString());
            IllegalStateException first = assertThrows(IllegalStateException.class, DatabaseConfig::databaseFile);
            assertTrue(first.getMessage(), first.getMessage().contains("thư mục dữ liệu"));
            assertThrows("the second call tries again and fails the same clean way",
                    IllegalStateException.class, DatabaseConfig::databaseFile);

            // Once the cause is fixed the same class works, in the same run.
            Path good = Files.createTempDirectory("lunavera-resolve-test").resolve("data");
            System.setProperty(DIR_PROPERTY, good.toString());
            assertEquals(good.resolve("lunavera.db").toAbsolutePath().normalize(),
                    DatabaseConfig.databaseFile().toAbsolutePath().normalize());
        } finally {
            restore(original);
            Files.deleteIfExists(blocker);
        }
    }

    @Test
    public void resetMakesTheFolderChangeableAndInitializeRunsAgainThere() throws Exception {
        TestDatabaseSupport.ensureReady();
        TestDatabaseSupport.requireSqlite();
        String original = System.getProperty(DIR_PROPERTY);
        Path first = DatabaseConfig.databaseFile();
        Path elsewhere = Files.createTempDirectory("lunavera-resolve-test").resolve("data");
        try {
            DatabaseConfig.reset();
            System.setProperty(DIR_PROPERTY, elsewhere.toString());
            assertEquals(elsewhere.resolve("lunavera.db").toAbsolutePath().normalize(),
                    DatabaseConfig.databaseFile().toAbsolutePath().normalize());
            assertFalse("nothing is created in the new place until it is used", Files.exists(elsewhere.resolve("lunavera.db")));
            DatabaseConfig.initialize();
            assertTrue("initialize built a database in the new folder", Files.size(elsewhere.resolve("lunavera.db")) > 0);
        } finally {
            restore(original);
        }
        assertEquals("back on the shared test database", first.toAbsolutePath().normalize(),
                DatabaseConfig.databaseFile().toAbsolutePath().normalize());
    }

    private static void restore(String original) {
        if (original == null) {
            System.clearProperty(DIR_PROPERTY);
        } else {
            System.setProperty(DIR_PROPERTY, original);
        }
        DatabaseConfig.reset();
        DatabaseConfig.initialize(); // the shared database again, as the other tests expect it
    }
}
