package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.23: status and role values inside SQL come from the enums through {@link SqlStatus}, not from hand-typed text.
 * Reads the sources as text, so it needs no database.
 */
public class SqlStatusTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");

    @Test
    public void everyConstantIsTheQuotedNameOfItsEnumValue() {
        assertEquals("'" + OrderStatus.OPEN.name() + "'", SqlStatus.OPEN);
        assertEquals("'" + OrderStatus.PAID.name() + "'", SqlStatus.PAID);
        assertEquals("'" + OrderStatus.CANCELLED.name() + "'", SqlStatus.CANCELLED);
        assertEquals("'" + AccountStatus.ACTIVE.name() + "'", SqlStatus.ACTIVE);
        assertEquals("'" + AccountStatus.LOCKED.name() + "'", SqlStatus.LOCKED);
        assertEquals("'" + TableStatus.EMPTY.name() + "'", SqlStatus.EMPTY);
        assertEquals("'" + TableStatus.OCCUPIED.name() + "'", SqlStatus.OCCUPIED);
        assertEquals("'" + Role.ADMIN.name() + "'", SqlStatus.ROLE_ADMIN);
        // And the literal text the database has always stored, so a rename of an enum value is noticed here, not in production.
        assertEquals("'PAID'", SqlStatus.PAID);
        assertEquals("'OPEN'", SqlStatus.OPEN);
        assertEquals("'CANCELLED'", SqlStatus.CANCELLED);
        assertEquals("'ACTIVE'", SqlStatus.ACTIVE);
        assertEquals("'EMPTY'", SqlStatus.EMPTY);
        assertEquals("'OCCUPIED'", SqlStatus.OCCUPIED);
        assertEquals("'ADMIN'", SqlStatus.ROLE_ADMIN);
    }

    @Test
    public void theTableScriptsKeepDefaultsThatMatchTheEnums() {
        String sqlite = String.join("\n", SqliteSchema.TABLES);
        String maria = String.join("\n", MariaDbSchema.TABLES);
        for (String ddl : new String[]{sqlite, maria}) {
            assertTrue(ddl, ddl.contains("status TEXT NOT NULL DEFAULT " + SqlStatus.ACTIVE)
                    || ddl.contains("status VARCHAR(20) NOT NULL DEFAULT " + SqlStatus.ACTIVE));
            assertTrue(ddl, ddl.contains("NOT NULL DEFAULT " + SqlStatus.OPEN));
            assertTrue(ddl, ddl.contains("NOT NULL DEFAULT " + SqlStatus.EMPTY));
        }
    }

    /** The rule from now on: no hand-typed status or role value in the statements of the data code. */
    @Test
    public void noHandTypedStatusValueIsLeftInTheDataCode() throws IOException {
        Pattern literal = Pattern.compile("'(OPEN|PAID|CANCELLED|ACTIVE|LOCKED|EMPTY|OCCUPIED|ADMIN)'");
        StringBuilder found = new StringBuilder();
        int scanned = 0;
        for (String folder : new String[]{"dao", "service", "controller", "util"}) {
            try (Stream<Path> files = Files.walk(MAIN.resolve(folder))) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    scanned++;
                    Matcher m = literal.matcher(Files.readString(file, StandardCharsets.UTF_8));
                    while (m.find()) {
                        found.append(file.getFileName()).append(": ").append(m.group()).append('\n');
                    }
                }
            }
        }
        assertTrue("the scan read no files; is the path right?", scanned > 20);
        assertEquals("write these as SqlStatus.<NAME> instead:\n" + found, 0, found.length());
    }
}
