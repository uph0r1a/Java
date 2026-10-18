package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TODO 6.26: the shop's data (customer phones, password hashes, the database password) must never be committed. The
 * {@code .gitignore} that protects it went missing once without anyone noticing; this fails if it does again, or if a
 * protective line is dropped. {@code .gitattributes} keeps the Maven wrapper's line endings right on Windows.
 */
public class GitIgnoreTest {

    private static List<String> lines(String file) throws IOException {
        Path path = Path.of(file);
        assertTrue(file + " is missing from the project folder", Files.isRegularFile(path));
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream().map(String::trim).toList();
    }

    @Test
    public void gitignoreProtectsTheDataAndTheBuildOutput() throws IOException {
        List<String> ignore = lines(".gitignore");
        for (String needed : new String[]{"/data/", "*.db", "*.db-wal", "*.db-shm", "logs/", "backups/",
                "database.properties", "target/", "*.iml", ".DS_Store", "Thumbs.db"}) {
            assertTrue(".gitignore should list " + needed, ignore.contains(needed));
        }
        assertFalse("the template database.properties.example must stay in git",
                ignore.contains("database.properties.example") || ignore.contains("*.example") || ignore.contains("*.properties"));
    }

    @Test
    public void gitattributesKeepsTheWrapperScriptsWorkingOnEveryPlatform() throws IOException {
        List<String> attributes = lines(".gitattributes");
        assertTrue(attributes.contains("* text=auto"));
        assertTrue(attributes.stream().anyMatch(l -> l.startsWith("mvnw") && l.contains("eol=lf")));
        assertTrue(attributes.stream().anyMatch(l -> l.startsWith("*.cmd") && l.contains("eol=crlf")));
    }
}
