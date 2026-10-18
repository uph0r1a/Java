package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** data/database.properties: which database, where, with which login. */
public class DatabaseSettingsTest {

    private static Properties props(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return p;
    }

    private static DatabaseSettings read(Properties file, Map<String, String> systemProperties) {
        return DatabaseSettings.fromProperties(file, systemProperties::get);
    }

    @Test
    public void withoutAnySettingTheDefaultIsMariaDbOnThisComputer() {
        DatabaseSettings s = read(new Properties(), Map.of());
        assertEquals(SqlDialect.MARIADB, s.dialect());
        assertTrue(s.isMariaDb());
        assertEquals("127.0.0.1", s.host());
        assertEquals(3306, s.port());
        assertEquals("lunavera", s.database());
        assertEquals("lunavera", s.user());
        assertEquals("", s.password());
        assertNull(s.importSqlite());
        assertEquals("mysqldump", s.mysqldump());
    }

    @Test
    public void aMariaDbFileGivesTheUrlAndTheLogin() {
        DatabaseSettings s = read(props("db.type", "mariadb", "db.host", "192.168.1.20", "db.port", "3307",
                "db.name", "quan_cafe", "db.user", "thu_ngan", "db.password", "s3cret!"), Map.of());
        assertTrue(s.isMariaDb());
        assertEquals("jdbc:mariadb://192.168.1.20:3307/quan_cafe?connectTimeout=5000", s.mariaDbUrl());
        assertEquals("thu_ngan", s.user());
        assertEquals("s3cret!", s.password());
    }

    @Test
    public void mysqlIsTheSameAsMariaDb() {
        assertTrue(read(props("db.type", "mysql"), Map.of()).isMariaDb());
        assertTrue(read(props("db.type", " MariaDB "), Map.of()).isMariaDb());
        assertEquals(SqlDialect.SQLITE, read(props("db.type", "SQLite"), Map.of()).dialect());
        assertEquals("a blank type is the default, MariaDB", SqlDialect.MARIADB, read(props("db.type", "  "), Map.of()).dialect());
    }

    @Test
    public void anUnknownTypeIsRefusedWithAClearMessage() {
        try {
            read(props("db.type", "oracle"), Map.of());
            fail("oracle must be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("oracle"));
        }
    }

    @Test
    public void aSystemPropertyBeatsTheFileAndABlankOneIsIgnored() {
        Properties file = props("db.type", "mariadb", "db.name", "from_file");
        assertEquals("from_cli", read(file, Map.of("lunavera.db.name", "from_cli")).database());
        assertEquals("from_file", read(file, Map.of("lunavera.db.name", "   ")).database());
        assertEquals(SqlDialect.SQLITE, read(file, Map.of("lunavera.db.type", "sqlite")).dialect());
    }

    @Test
    public void aBadPortIsRefused() {
        for (String port : new String[]{"abc", "0", "70000", "-1", "33.06"}) {
            try {
                read(props("db.port", port), Map.of());
                fail("port " + port + " must be refused");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("db.port"));
            }
        }
    }

    @Test
    public void aNameOrHostThatCouldBreakTheUrlIsRefusedForMariaDb() {
        for (String name : new String[]{"a;drop", "x/../y", "bad name", "a?b=c", ""}) {
            if (name.isEmpty()) continue; // blank falls back to the default name
            try {
                read(props("db.type", "mariadb", "db.name", name), Map.of());
                fail("name " + name + " must be refused");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("db.name"));
            }
        }
        for (String host : new String[]{"host/path", "a b", "h?x=1", "h#frag"}) {
            try {
                read(props("db.type", "mariadb", "db.host", host), Map.of());
                fail("host " + host + " must be refused");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("db.host"));
            }
        }
    }

    @Test
    public void theNameAndHostAreNotCheckedWhenTheShopUsesSqlite() {
        // they are never used, so a leftover odd value in the file must not stop the app starting
        assertEquals(SqlDialect.SQLITE, read(props("db.type", "sqlite", "db.name", "a;b", "db.host", "x y"), Map.of()).dialect());
    }

    @Test
    public void theImportPathIsKeptOnlyWhenGiven() {
        assertEquals("data/lunavera.db", read(props("db.import.sqlite", " data/lunavera.db "), Map.of()).importSqlite());
        assertNull(read(props("db.import.sqlite", "  "), Map.of()).importSqlite());
    }

    @Test
    public void theDescriptionNeverContainsThePassword() {
        DatabaseSettings s = read(props("db.type", "mariadb", "db.password", "topsecret"), Map.of());
        assertFalse(s.describe().contains("topsecret"));
        assertFalse(s.toString().contains("topsecret"));
        assertTrue(s.describe().contains("lunavera@127.0.0.1:3306/lunavera"));
        assertEquals("SQLite", read(props("db.type", "sqlite"), Map.of()).describe());
    }

    @Test
    public void thePhpMyAdminAddressDefaultsToTheLocalOne() {
        assertEquals("http://localhost/phpmyadmin/", read(new Properties(), Map.of()).phpMyAdminUrl());
        assertEquals("https://db.example.com/pma/",
                read(props("db.phpmyadmin", "https://db.example.com/pma/"), Map.of()).phpMyAdminUrl());
        assertEquals("http://localhost:8080/phpmyadmin/",
                read(new Properties(), Map.of("lunavera.db.phpmyadmin", "http://localhost:8080/phpmyadmin/")).phpMyAdminUrl());
    }

    @Test
    public void thePhpMyAdminAddressMustBeAWebAddress() {
        for (String bad : new String[]{"file:///C:/Windows/System32/cmd.exe", "javascript:alert(1)", "ftp://x/y",
                "localhost/phpmyadmin", "http://local host/", "http://x/" + "a".repeat(300)}) {
            try {
                read(props("db.phpmyadmin", bad), Map.of());
                fail("must be refused: " + bad);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("db.phpmyadmin"));
            }
        }
    }
}
