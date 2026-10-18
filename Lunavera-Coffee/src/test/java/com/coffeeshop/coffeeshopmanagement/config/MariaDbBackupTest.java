package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MariaDbBackupTest {

    private static DatabaseSettings settings(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return DatabaseSettings.fromProperties(p, Map.<String, String>of()::get);
    }

    @Test
    public void theDumpCommandNamesTheServerTheUserAndTheDatabase() {
        DatabaseSettings s = settings("db.type", "mariadb", "db.host", "10.0.0.5", "db.port", "3307",
                "db.name", "quan", "db.user", "thungan", "db.mysqldump", "C:\\xampp\\mysql\\bin\\mysqldump.exe");
        List<String> command = MariaDbBackup.command(s, Path.of("backup.sql"));
        assertEquals("C:\\xampp\\mysql\\bin\\mysqldump.exe", command.get(0));
        assertTrue(command.contains("--host=10.0.0.5"));
        assertTrue(command.contains("--port=3307"));
        assertTrue(command.contains("--user=thungan"));
        assertTrue(command.contains("--single-transaction"));
        assertTrue(command.contains("--default-character-set=utf8mb4"));
        assertEquals("the database name comes last", "quan", command.get(command.size() - 1));
    }

    @Test
    public void theResultFileIsAbsoluteAndThePasswordNeverOnTheCommandLine() {
        DatabaseSettings s = settings("db.type", "mariadb", "db.password", "topsecret");
        List<String> command = MariaDbBackup.command(s, Path.of("out", "b.sql"));
        String resultFile = command.stream().filter(a -> a.startsWith("--result-file=")).findFirst().orElseThrow();
        assertTrue(resultFile, Path.of(resultFile.substring("--result-file=".length())).isAbsolute());
        for (String argument : command) {
            assertFalse("the password must travel in MYSQL_PWD, not here: " + argument, argument.contains("topsecret"));
        }
    }
}
