package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertEquals("jdbc:mariadb://192.168.1.20:3307/quan_cafe?connectTimeout=5000&socketTimeout=30000&sslMode=disable", s.mariaDbUrl());
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

    // ---------------------------------------------------------------- TODO 2A.26: TLS and the read timeout

    private static DatabaseSettings remote(String... more) {
        String[] base = {"db.type", "mariadb", "db.host", "192.168.1.20", "db.name", "quan_cafe"};
        String[] all = new String[base.length + more.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return read(props(all), Map.of());
    }

    private static void refused(String what, String mustMention, String... keyValues) {
        try {
            remote(keyValues);
            fail(what + " must be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(mustMention));
        }
    }

    @Test
    public void withoutAnySslSettingTheConnectionIsNotEncryptedAndHasAReadTimeout() {
        DatabaseSettings s = remote();
        assertEquals(DatabaseSettings.SslMode.DISABLE, s.sslMode());
        assertFalse(s.usesTls());
        assertNull(s.sslServerCert());
        assertEquals(30_000, s.socketTimeoutMillis());
        assertEquals(DatabaseSettings.DEFAULT_SOCKET_TIMEOUT_MILLIS, s.socketTimeoutMillis());
    }

    @Test
    public void sslTrueMeansTlsWithTheServerCertificateAndNameChecked() {
        DatabaseSettings s = remote("db.ssl", "true");
        assertEquals(DatabaseSettings.SslMode.VERIFY_FULL, s.sslMode());
        assertTrue(s.usesTls());
        assertEquals("jdbc:mariadb://192.168.1.20:3306/quan_cafe?connectTimeout=5000&socketTimeout=30000&sslMode=verify-full", s.mariaDbUrl());
        assertEquals(DatabaseSettings.SslMode.VERIFY_FULL, remote("db.ssl", " YES ").sslMode());
        assertEquals(DatabaseSettings.SslMode.DISABLE, remote("db.ssl", "false").sslMode());
        assertEquals(DatabaseSettings.SslMode.DISABLE, remote("db.ssl", "0").sslMode());
    }

    @Test
    public void anExplicitSslModeIsUsedAndBeatsPlainSslTrue() {
        assertEquals(DatabaseSettings.SslMode.TRUST, remote("db.sslMode", "trust").sslMode());
        assertEquals(DatabaseSettings.SslMode.VERIFY_CA, remote("db.sslMode", "Verify-CA").sslMode());
        assertEquals(DatabaseSettings.SslMode.VERIFY_CA, remote("db.ssl", "true", "db.sslMode", "verify-ca").sslMode());
        assertEquals(DatabaseSettings.SslMode.DISABLE, remote("db.sslMode", "disable").sslMode());
        assertTrue(remote("db.sslMode", "trust").mariaDbUrl().endsWith("&sslMode=trust"));
    }

    @Test
    public void anUnknownSslModeOrAContradictionIsRefusedWithAClearMessage() {
        refused("sslMode=on", "db.sslMode", "db.sslMode", "on");
        refused("sslMode=require", "db.sslMode", "db.sslMode", "require");
        refused("ssl=maybe", "db.ssl", "db.ssl", "maybe");
        refused("ssl=true with disable", "db.ssl", "db.ssl", "true", "db.sslMode", "disable");
    }

    @Test
    public void theServerCertificateMustExistAndGoesIntoTheUrlWhenTheModeChecksIt() throws IOException {
        Path cert = Files.createTempFile("my ca&cert", ".pem");
        DatabaseSettings s = remote("db.sslMode", "verify-ca", "db.sslServerCert", cert.toString());
        assertEquals(cert.toAbsolutePath().normalize().toString(), s.sslServerCert());
        String url = s.mariaDbUrl();
        assertTrue(url, url.contains("&sslMode=verify-ca&serverSslCert="));
        assertTrue("a space and an ampersand are escaped: " + url, url.contains("my%20ca%26cert"));
        assertFalse(url, url.contains(" "));
        assertFalse("a Windows path is written with forward slashes", url.contains("\\"));
        // a missing file is refused when it would be used ...
        refused("missing certificate", "db.sslServerCert", "db.ssl", "true", "db.sslServerCert", cert + ".missing");
        // ... and ignored when the mode does not check certificates
        DatabaseSettings trust = remote("db.sslMode", "trust", "db.sslServerCert", cert + ".missing");
        assertNull(trust.sslServerCert());
        assertFalse(trust.mariaDbUrl().contains("serverSslCert"));
    }

    @Test
    public void thePathOfACertificateIsEscapedForTheUrl() {
        assertEquals("C:/Program%20Files/ca.pem", DatabaseSettings.encodeForUrl("C:\\Program Files\\ca.pem"));
        assertEquals("/etc/ssl/a%25b%26c%3Dd%23e%3Ff.pem", DatabaseSettings.encodeForUrl("/etc/ssl/a%b&c=d#e?f.pem"));
    }

    @Test
    public void theReadTimeoutCanBeChangedOrSwitchedOffButNotSetToNonsense() {
        assertEquals(5_000, remote("db.socketTimeoutMs", "5000").socketTimeoutMillis());
        assertTrue(remote("db.socketTimeoutMs", "5000").mariaDbUrl().contains("&socketTimeout=5000&"));
        assertEquals("0 means wait for ever", 0, remote("db.socketTimeoutMs", "0").socketTimeoutMillis());
        assertEquals(1_000, remote("db.socketTimeoutMs", "1000").socketTimeoutMillis());
        assertEquals(3_600_000, remote("db.socketTimeoutMs", "3600000").socketTimeoutMillis());
        for (String bad : new String[]{"abc", "-1", "999", "3600001", "30s", "1.5"}) {
            refused("timeout " + bad, "db.socketTimeoutMs", "db.socketTimeoutMs", bad);
        }
    }

    @Test
    public void aSystemPropertyCanTurnTlsOn() {
        DatabaseSettings s = read(props("db.type", "mariadb", "db.host", "192.168.1.20"), Map.of("lunavera.db.ssl", "true"));
        assertEquals(DatabaseSettings.SslMode.VERIFY_FULL, s.sslMode());
        assertEquals(1_234, read(props("db.type", "mariadb"), Map.of("lunavera.db.socketTimeoutMs", "1234")).socketTimeoutMillis());
    }

    @Test
    public void sqliteIgnoresTheTlsAndTimeoutSettings() {
        DatabaseSettings s = read(props("db.type", "sqlite", "db.ssl", "banana", "db.sslMode", "x", "db.socketTimeoutMs", "abc"), Map.of());
        assertEquals(SqlDialect.SQLITE, s.dialect());
        assertFalse(s.usesTls());
        assertNull(s.connectionSecurityNote());
        assertNull(s.insecureRemoteWarning());
    }

    @Test
    public void aServerOnThisComputerNeedsNoTls() {
        for (String host : new String[]{"localhost", "LOCALHOST", "127.0.0.1", "127.1.2.3", "::1"}) {
            DatabaseSettings s = read(props("db.type", "mariadb", "db.host", host), Map.of());
            assertTrue(host, s.isLoopbackHost());
            assertNull(host, s.insecureRemoteWarning());
            assertNull(host, s.connectionSecurityNote());
        }
        for (String host : new String[]{"192.168.1.20", "db.shop.local", "10.0.0.1", "1270.0.0.1", "example.com"}) {
            assertFalse(host, read(props("db.type", "mariadb", "db.host", host), Map.of()).isLoopbackHost());
        }
    }

    @Test
    public void aServerOnAnotherComputerWithoutTlsIsWarnedAboutAndTheWarningHasNoPassword() {
        DatabaseSettings s = remote("db.password", "topsecret");
        String warning = s.insecureRemoteWarning();
        assertTrue(warning, warning != null && warning.contains("192.168.1.20") && warning.contains("db.ssl=true"));
        assertFalse(warning, warning.contains("topsecret"));
        assertEquals("KHÔNG mã hóa", s.connectionSecurityNote());
        assertNull("with TLS there is nothing to warn about", remote("db.ssl", "true").insecureRemoteWarning());
        assertNull(remote("db.sslMode", "trust").insecureRemoteWarning());
    }

    @Test
    public void theLoginLineSaysHowTheConnectionIsProtected() {
        assertEquals("mã hóa TLS", remote("db.ssl", "true").connectionSecurityNote());
        assertEquals("mã hóa TLS", remote("db.sslMode", "verify-ca").connectionSecurityNote());
        assertTrue(remote("db.sslMode", "trust").connectionSecurityNote().contains("chưa kiểm tra"));
        assertEquals("a local server with TLS still says so", "mã hóa TLS",
                read(props("db.type", "mariadb", "db.ssl", "true"), Map.of()).connectionSecurityNote());
    }

    @Test
    public void anIpv6AddressIsPutInSquareBracketsInTheUrl() {
        DatabaseSettings s = read(props("db.type", "mariadb", "db.host", "fe80::1", "db.name", "lunavera"), Map.of());
        assertTrue(s.mariaDbUrl(), s.mariaDbUrl().startsWith("jdbc:mariadb://[fe80::1]:3306/lunavera?"));
        assertTrue(read(props("db.type", "mariadb", "db.host", "::1"), Map.of()).mariaDbUrl().startsWith("jdbc:mariadb://[::1]:3306/"));
        assertTrue("a name or an IPv4 address is left as it is",
                read(props("db.type", "mariadb", "db.host", "10.0.0.5"), Map.of()).mariaDbUrl().startsWith("jdbc:mariadb://10.0.0.5:3306/"));
    }
}
