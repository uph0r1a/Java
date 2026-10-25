package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

/**
 * TODO 2.82: {@code data/database.properties} is read literally. A generated password with a backslash used to be silently
 * changed ({@code ab\cd} became {@code abcd}) and a password ending in a backslash swallowed the next setting, which could send
 * a SQLite shop to MariaDB. Needs no database or file: the text is passed in.
 */
public class DatabasePropertiesFileTest {

    private static String password(String fileText) {
        return DatabaseSettings.fromProperties(DatabaseSettings.parseLiteral(fileText), k -> null).password();
    }

    @Test
    public void aBackslashIsAnOrdinaryCharacter() {
        assertEquals("ab\\cd", password("db.type=sqlite\ndb.password=ab\\cd\n"));
        assertEquals("ab\\\\cd", password("db.type=sqlite\ndb.password=ab\\\\cd\n"));
    }

    @Test
    public void aUnicodeEscapeIsNotInterpreted() {
        assertEquals("x\\u0041y", password("db.type=sqlite\ndb.password=x\\u0041y\n"));
    }

    @Test
    public void spacesAroundThePasswordAreKept() {
        assertEquals("  lead", password("db.type=sqlite\ndb.password=  lead\n"));
        assertEquals("trail   ", password("db.type=sqlite\ndb.password=trail   \n"));
    }

    @Test
    public void aPasswordWithoutAnythingButSpacesMeansNoPassword() {
        assertEquals("", password("db.type=sqlite\ndb.password=   \n"));
        assertEquals("", password("db.type=sqlite\ndb.password=\n"));
        assertEquals("", password("db.type=sqlite\n"));
    }

    @Test
    public void aBackslashAtTheEndOfTheLineDoesNotSwallowTheNextSetting() {
        String file = "db.password=abc\\\ndb.type=sqlite\n";
        Properties parsed = DatabaseSettings.parseLiteral(file);
        assertEquals("abc\\", parsed.getProperty("db.password"));
        assertEquals("the next setting is still there", "sqlite", parsed.getProperty("db.type"));
        assertFalse("and the shop is not sent to MariaDB",
                DatabaseSettings.fromProperties(parsed, k -> null).isMariaDb());
    }

    @Test
    public void commentCharactersEqualsAndColonsInsideThePasswordAreFine() {
        assertEquals("a#b!c=d:e", password("db.type=sqlite\ndb.password=a#b!c=d:e\n"));
    }

    @Test
    public void commentLinesBlankLinesAndALineWithoutEqualsAreIgnored() {
        Properties p = DatabaseSettings.parseLiteral("# a comment\n\n   ! another\nnot a setting\n  db.type = sqlite \n");
        assertEquals(1, p.size());
        assertEquals("the value is kept as written; fromProperties trims the other settings", " sqlite ", p.getProperty("db.type"));
        assertFalse(DatabaseSettings.fromProperties(p, k -> null).isMariaDb());
    }

    @Test
    public void windowsLineEndsAndANotepadByteOrderMarkAreHandled() {
        Properties p = DatabaseSettings.parseLiteral("\uFEFFdb.type=sqlite\r\ndb.password=pw\r\n");
        assertEquals("sqlite", p.getProperty("db.type"));
        assertEquals("pw", p.getProperty("db.password"));
    }

    @Test
    public void aWindowsPathWithSingleBackslashesSurvives() {
        Properties p = DatabaseSettings.parseLiteral("db.mysqldump=C:\\xampp\\mysql\\bin\\mysqldump.exe\n");
        assertEquals("C:\\xampp\\mysql\\bin\\mysqldump.exe", p.getProperty("db.mysqldump"));
    }

    @Test
    public void theSystemPropertyOverrideStillWinsAndIsAlsoUsedAsTyped() {
        DatabaseSettings s = DatabaseSettings.fromProperties(DatabaseSettings.parseLiteral("db.type=sqlite\ndb.password=file\n"),
                k -> k.equals("lunavera.db.password") ? " over\\ride " : null);
        assertEquals(" over\\ride ", s.password());
    }
}
