package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.82 (with 6.21): no screen class touches the database layer itself. A class in {@code controller} may not use
 * {@code DatabaseConfig} (connections, the kind of database, where it lives) or {@code java.sql}; it asks a DAO or a service, and for
 * facts about the database {@code DatabaseInfo}. The allow-list is empty: four screens used to import it, and the number can only
 * stay at zero. Reads the source files, like {@code ControllerSizeTest}.
 */
public class ScreensStayAwayFromTheDatabaseTest {

    private static final Path CONTROLLERS = Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/controller");
    /** Controllers that may still use the database layer directly: none. */
    private static final List<String> ALLOWED = List.of();

    @Test
    public void noScreenUsesDatabaseConfigOrJavaSql() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                String name = file.getFileName().toString().replace(".java", "");
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                boolean touches = text.contains("config.DatabaseConfig") || text.contains("import java.sql.") || text.contains("java.sql.Connection");
                if (touches && !ALLOWED.contains(name)) offenders.add(name);
            }
        }
        assertTrue("these screens use DatabaseConfig or java.sql directly; put the call in a DAO or a service (or DatabaseInfo): " + offenders,
                offenders.isEmpty());
    }
}
