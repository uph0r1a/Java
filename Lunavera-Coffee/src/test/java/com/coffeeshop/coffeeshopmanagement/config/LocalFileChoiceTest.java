package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * TODO 5.40: first start with no database server. The rule for offering the local file, the file it writes, and (end to end, on a
 * temporary folder, without a screen) that a computer with no settings file goes from "MariaDB, nothing answering" to a working
 * local database after the file is written.
 */
public class LocalFileChoiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static DatabaseSettings settings(String type) {
        Properties p = new Properties();
        if (type != null) p.setProperty("db.type", type);
        return DatabaseSettings.fromProperties(p, key -> null);
    }

    // ---- when it is offered

    @Test
    public void offeredWhenTheDefaultServerIsUsedAndNobodyNamedTheDatabase() {
        assertTrue(LocalFileChoice.applies(settings(null), false, false));
    }

    @Test
    public void notOfferedWhenASettingsFileExists() {
        assertFalse("a shop with a settings file keeps the old text and fixes its server",
                LocalFileChoice.applies(settings(null), true, false));
        assertFalse(LocalFileChoice.applies(settings("mariadb"), true, false));
    }

    @Test
    public void notOfferedWhenTheTypeWasSetOnPurposeByAProperty() {
        assertFalse(LocalFileChoice.applies(settings(null), false, true));
    }

    @Test
    public void notOfferedToAShopAlreadyOnSqlite() {
        assertFalse(LocalFileChoice.applies(settings("sqlite"), false, false));
    }

    @Test
    public void notOfferedWithoutSettings() {
        assertFalse(LocalFileChoice.applies(null, false, false));
    }

    // ---- what it writes

    @Test
    public void theFileIsMadeWithItsFolderAndReadsAsSqlite() throws IOException {
        Path dir = tmp.getRoot().toPath().resolve("not").resolve("there").resolve("yet");
        Path file = LocalFileChoice.write(dir);
        assertEquals(dir.resolve("database.properties"), file);
        Properties read = DatabaseSettings.parseLiteral(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("sqlite", read.getProperty("db.type"));
        assertEquals(SqlDialect.SQLITE, DatabaseSettings.fromProperties(read, key -> null).dialect());
    }

    @Test
    public void anExistingSettingsFileIsNeverReplaced() throws IOException {
        Path dir = tmp.newFolder("shop").toPath();
        Path file = dir.resolve("database.properties");
        String original = "db.type=mariadb\ndb.password=secret\n";
        Files.writeString(file, original, StandardCharsets.UTF_8);
        IOException e = assertThrows(IOException.class, () -> LocalFileChoice.write(dir));
        assertTrue(e.getMessage(), e.getMessage().contains("already exists"));
        assertEquals("left exactly as it was", original, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    public void theFileHoldsNoPasswordAndSaysHowToGoBackToAServer() {
        String text = LocalFileChoice.fileText();
        assertFalse(text.toLowerCase().contains("password"));
        assertTrue(text.contains("database.properties.example"));
        assertTrue(text.contains("docs/DATABASE.md"));
    }

    // ---- end to end

    @Test
    public void noSettingsFileAndNoServerBecomesAWorkingLocalDatabase() throws Exception {
        TestDatabaseSupport.ensureReady();
        TestDatabaseSupport.requireSqlite();
        String dirProperty = System.getProperty("lunavera.dataDir");
        String typeProperty = System.getProperty("lunavera.db.type");
        Path shop = tmp.newFolder("first-start").toPath().resolve("data");
        try {
            DatabaseConfig.reset();
            System.setProperty("lunavera.dataDir", shop.toString());
            System.clearProperty("lunavera.db.type"); // a real computer has no such property
            assertEquals("the default is the server", SqlDialect.MARIADB, DatabaseConfig.dialect());
            assertTrue("so the local file is offered", LocalFileChoice.applies());
            assertFalse(LocalFileChoice.localFileExists());

            LocalFileChoice.write();
            DatabaseConfig.reset();
            assertFalse("once the file is written nothing is offered again", LocalFileChoice.applies());
            assertEquals(SqlDialect.SQLITE, DatabaseConfig.dialect());
            DatabaseConfig.initialize();
            assertTrue("the local database was built", Files.size(shop.resolve("lunavera.db")) > 0);
            assertTrue(LocalFileChoice.localFileExists());
        } finally {
            if (typeProperty == null) System.clearProperty("lunavera.db.type"); else System.setProperty("lunavera.db.type", typeProperty);
            if (dirProperty == null) System.clearProperty("lunavera.dataDir"); else System.setProperty("lunavera.dataDir", dirProperty);
            DatabaseConfig.reset();
            DatabaseConfig.initialize(); // the shared test database again, as the other tests expect it
        }
    }
}
