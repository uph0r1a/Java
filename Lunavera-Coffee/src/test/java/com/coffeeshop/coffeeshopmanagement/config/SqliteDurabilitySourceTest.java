package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 2.92: a paid sale survives a power cut, so SQLite is told {@code synchronous = FULL} on every connection. These read the
 * source (no SQLite driver needed); {@link SqliteDurabilityTest} asks a real connection what it answers.
 */
public class SqliteDurabilitySourceTest {

    private static final Path CONFIG = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "config", "DatabaseConfig.java");

    private static String source() throws IOException {
        return Files.readString(CONFIG, StandardCharsets.UTF_8);
    }

    @Test
    public void theSettingIsFull() {
        assertEquals("FULL", DatabaseConfig.SQLITE_SYNCHRONOUS);
    }

    @Test
    public void nothingStillAsksForNormal() throws IOException {
        assertFalse("PRAGMA synchronous = NORMAL would let a power cut take back the last paid sales", source().contains("synchronous = NORMAL"));
    }

    @Test
    public void theSettingIsSentOnEveryConnectionNotOnlyWhenWalIsOn() throws IOException {
        String source = source();
        int start = source.indexOf("static Connection configureSqlite(");
        int end = source.indexOf("registerSearchKeyFunction(connection);", start);
        String body = source.substring(start, end);
        assertTrue(body.contains("\"PRAGMA synchronous = \" + SQLITE_SYNCHRONOUS"));
        assertFalse("it must not wait for walActive (item 2.92: FULL also when WAL was refused)", body.contains("if (walActive)"));
    }

    @Test
    public void theDocumentationTellsMariaDbOwnersWhatToKeep() throws IOException {
        String doc = Files.readString(Path.of("docs", "DATABASE.md"), StandardCharsets.UTF_8);
        assertTrue(doc.contains("innodb_flush_log_at_trx_commit = 1"));
        assertTrue(doc.contains("synchronous = FULL"));
    }
}
