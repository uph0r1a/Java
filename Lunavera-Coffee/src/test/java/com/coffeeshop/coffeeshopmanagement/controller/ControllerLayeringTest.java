package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.DatabaseInfo;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.82: a screen does not touch the database. No class in {@code controller/} may use {@code DatabaseConfig} (the connection
 * and the settings) or {@code java.sql}; what a screen needs from there is asked of a service ({@code DatabaseInfo} for the kind and
 * the place of the database) or of a DAO through {@code Async}. Reads the sources as text, like {@link ControllerSizeTest}, so it
 * needs no JavaFX and no database.
 *
 * <p>The allow-list is empty: the four screens that broke the rule when the item was written ({@code BackupDialogs},
 * {@code FirstRunDialog}, {@code LoginController}, {@code AdminDashboardController}) were moved. A name may be added here only to
 * let a class through while it is being moved, and never stays.
 */
public class ControllerLayeringTest {

    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    /** Screens allowed to break the rule for now. Empty: it can only get shorter, and a new class never goes on it. */
    static final Set<String> ALLOWED = Set.of();

    private static final List<String> FORBIDDEN = List.of("config.DatabaseConfig", "java.sql.");

    /** The source with its comments and string literals blanked, so that a sentence that names {@code DatabaseConfig} is not a use of it. */
    static String code(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i += 2;
            } else if (c == '"') {
                i++;
                while (i < n && source.charAt(i) != '"') i += source.charAt(i) == '\\' ? 2 : 1;
                i++;
                out.append("\"\"");
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** The forbidden names a source uses, as an import or written out in full. */
    static List<String> violations(String source) {
        String code = code(source);
        List<String> found = new ArrayList<>();
        for (String name : FORBIDDEN) {
            if (code.contains(name)) found.add(name);
        }
        return found;
    }

    @Test
    public void noScreenTouchesTheDatabaseLayer() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".java"))::iterator) {
                String name = file.getFileName().toString().replace(".java", "");
                if (ALLOWED.contains(name)) continue;
                List<String> used = violations(Files.readString(file, StandardCharsets.UTF_8));
                if (!used.isEmpty()) offenders.add(name + " uses " + used);
            }
        }
        assertTrue("controller classes must ask a service instead of using the database layer (TODO 6.82): " + offenders, offenders.isEmpty());
    }

    @Test
    public void theAllowListNamesRealClassesOnly() throws IOException {
        for (String name : ALLOWED) {
            assertTrue(name + " is on the allow-list but is not a controller class (a moved class must be taken off)",
                    Files.exists(CONTROLLERS.resolve(name + ".java")));
        }
    }

    // ------------------------------------------------------------------ the detector itself

    @Test
    public void anImportAndAWrittenOutNameAreBothFound() {
        assertEquals(List.of("config.DatabaseConfig"),
                violations("import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;\nclass A {}"));
        assertEquals(List.of("config.DatabaseConfig"),
                violations("class A { boolean b = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite(); }"));
        assertEquals(List.of("java.sql."), violations("import java.sql.SQLException;\nclass A {}"));
        assertEquals(List.of("java.sql."), violations("class A { void f() throws java.sql.SQLException {} }"));
    }

    @Test
    public void aCommentOrATextThatOnlyNamesThemIsNotAUse() {
        assertTrue(violations("// the DatabaseConfig is not used here: config.DatabaseConfig\nclass A {}").isEmpty());
        assertTrue(violations("/* java.sql.Connection is behind the DAO */ class A {}").isEmpty());
        assertTrue(violations("/** see config.DatabaseConfig */ class A { String s = \"java.sql.Types\"; }").isEmpty());
        assertTrue(violations("class A { String s = \"say \\\" config.DatabaseConfig\"; }").isEmpty());
    }

    @Test
    public void aClassThatOnlyUsesServicesPasses() {
        assertTrue(violations("import com.coffeeshop.coffeeshopmanagement.service.DatabaseInfo;\nclass A { boolean b = DatabaseInfo.isSqlite(); }").isEmpty());
        assertFalse(violations("import java.sql.Connection;").isEmpty());
    }

    // ------------------------------------------------------------------ what moved

    @Test
    public void theDefaultAdministratorPasswordIsRecognised() {
        assertTrue(DatabaseInfo.isFactoryAdminPassword("Admin@123"));
        assertFalse(DatabaseInfo.isFactoryAdminPassword("admin@123"));
        assertFalse(DatabaseInfo.isFactoryAdminPassword(""));
        assertFalse(DatabaseInfo.isFactoryAdminPassword(null));
    }
}
