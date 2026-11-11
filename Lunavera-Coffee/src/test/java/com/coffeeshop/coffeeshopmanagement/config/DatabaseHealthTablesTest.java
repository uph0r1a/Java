package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.75: every table that the dashboard's health line counts is a table of both schemas, so a renamed table cannot leave a count
 * that silently fails. It only reads the sources: no database and no JavaFX.
 */
public class DatabaseHealthTablesTest {

    private static String source(String file) throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/config", file)),
                StandardCharsets.UTF_8);
    }

    @Test
    public void everyCountedTableIsATableOfBothSchemas() throws IOException {
        String sqlite = source("SqliteSchema.java");
        String maria = source("MariaDbSchema.java");
        assertTrue(DatabaseHealth.COUNTED_TABLES.size() >= 8);
        for (String table : DatabaseHealth.COUNTED_TABLES) {
            Pattern create = Pattern.compile("CREATE TABLE IF NOT EXISTS\\s+" + Pattern.quote(table) + "(?![A-Za-z0-9_])");
            assertTrue(table + " is not created by SqliteSchema", create.matcher(sqlite).find());
            assertTrue(table + " is not created by MariaDbSchema", create.matcher(maria).find());
        }
    }
}
