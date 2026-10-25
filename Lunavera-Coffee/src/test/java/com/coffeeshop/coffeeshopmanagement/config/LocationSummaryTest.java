package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 5.25: the line on the login screen that says which data this is. Without a database: only the text is checked, and that it
 * never carries the user name or the password.
 */
public class LocationSummaryTest {

    private static DatabaseSettings sqlite() {
        return new DatabaseSettings(SqlDialect.SQLITE, "127.0.0.1", 3306, "lunavera", "root", "TOP-SECRET", null, null, null);
    }

    private static DatabaseSettings maria() {
        return new DatabaseSettings(SqlDialect.MARIADB, "db.shop.local", 3307, "quan_ca_phe", "shopuser", "TOP-SECRET", null, null, null);
    }

    @Test
    public void sqliteShowsTheDataFolderAndTheVersion() {
        Path folder = Path.of("some", "data").toAbsolutePath().normalize();
        String text = DatabaseConfig.locationSummary(sqlite(), Path.of("some", "data"), 3);
        assertTrue(text, text.startsWith("Dữ liệu: " + folder));
        assertTrue(text, text.endsWith("cấu trúc v3"));
    }

    @Test
    public void mariaDbShowsTheServerAndDatabaseNameButNeverTheUserOrPassword() {
        String text = DatabaseConfig.locationSummary(maria(), Path.of("data"), 2);
        assertTrue(text, text.contains("db.shop.local:3307/quan_ca_phe"));
        assertFalse(text, text.contains("shopuser"));
        assertFalse(text, text.contains("TOP-SECRET"));
        assertFalse("the local data folder is not where MariaDB data is", text.contains(Path.of("data").toAbsolutePath().toString()));
    }

    @Test
    public void thePasswordNeverAppearsInEitherCase() {
        assertFalse(DatabaseConfig.locationSummary(sqlite(), Path.of("data"), 2).contains("TOP-SECRET"));
        assertFalse(DatabaseConfig.locationSummary(maria(), Path.of("data"), 2).contains("TOP-SECRET"));
    }

    @Test
    public void aServerOnAnotherComputerWithoutTlsIsLabelledAsNotEncrypted() {
        String text = DatabaseConfig.locationSummary(maria(), Path.of("data"), 2);
        assertTrue(text, text.contains("KHÔNG mã hóa"));
        assertTrue(text, text.endsWith("cấu trúc v2"));
    }

    @Test
    public void tlsIsShownAndTheLocalServerWithoutItSaysNothingExtra() {
        DatabaseSettings tls = new DatabaseSettings(SqlDialect.MARIADB, "db.shop.local", 3307, "quan_ca_phe", "shopuser", "TOP-SECRET",
                null, null, null, DatabaseSettings.SslMode.VERIFY_FULL, null, 30_000);
        String text = DatabaseConfig.locationSummary(tls, Path.of("data"), 2);
        assertTrue(text, text.contains("mã hóa TLS"));
        assertFalse(text, text.contains("KHÔNG"));
        DatabaseSettings local = new DatabaseSettings(SqlDialect.MARIADB, "127.0.0.1", 3306, "lunavera", "root", "TOP-SECRET", null, null, null);
        assertEquals("Dữ liệu: máy chủ 127.0.0.1:3306/lunavera  ·  cấu trúc v2", DatabaseConfig.locationSummary(local, Path.of("data"), 2));
        assertEquals("SQLite has no connection to protect", false,
                DatabaseConfig.locationSummary(sqlite(), Path.of("data"), 2).contains("TLS"));
    }
}
