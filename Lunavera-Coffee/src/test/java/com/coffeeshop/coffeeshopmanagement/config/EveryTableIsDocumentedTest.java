package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.69: every table the schemas create is named in {@code docs/DATABASE.md}, so a new table cannot ship without a description. It reads the
 * sources (no database), so it needs no server and finds a table that only the MariaDB schema has.
 */
public class EveryTableIsDocumentedTest {

    private static final Pattern CREATE = Pattern.compile("CREATE TABLE IF NOT EXISTS\\s+([a-z_]+)");

    private static Set<String> tablesIn(String file) throws IOException {
        Path path = Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/config", file);
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Matcher m = CREATE.matcher(text);
        Set<String> names = new TreeSet<>();
        while (m.find()) names.add(m.group(1));
        return names;
    }

    private static boolean named(String document, String table) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(table) + "(?![A-Za-z0-9_])").matcher(document).find();
    }

    @Test
    public void everyTableOfBothSchemasIsNamedInTheDatabaseGuide() throws IOException {
        String guide = new String(Files.readAllBytes(Paths.get("docs/DATABASE.md")), StandardCharsets.UTF_8);
        Set<String> tables = tablesIn("SqliteSchema.java");
        tables.addAll(tablesIn("MariaDbSchema.java"));
        assertTrue("the schemas were not read: " + tables, tables.size() > 20);
        Set<String> missing = new TreeSet<>();
        for (String table : tables) if (!named(guide, table)) missing.add(table);
        assertTrue("tables not named in docs/DATABASE.md (describe them there): " + missing, missing.isEmpty());
    }

    @Test
    public void theCheckSeesAMissingTable() {
        assertTrue(named("a `audit_log` table", "audit_log"));
        assertTrue("a longer name does not count", !named("the audit_log_old table", "audit_log"));
        assertTrue(!named("nothing here", "audit_log"));
    }
}
