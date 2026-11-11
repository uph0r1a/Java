package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 6.74 on the real (SQLite test) database: settings live in the {@code settings} table, not in the names of {@code app_flags} rows. */
public class SettingsTableDatabaseTest {

    private static final List<String> PREFIXES = List.of(ShopSettings.PREFIX, ShopSettings.ADDRESS_PREFIX, ShopSettings.PHONE_PREFIX,
            ShopSettings.TAX_PREFIX, ShopSettings.LOW_STOCK_PREFIX);

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void putBack() throws Exception {
        try (Connection c = DatabaseConfig.getConnection()) {
            for (String prefix : PREFIXES) {
                try (PreparedStatement p = c.prepareStatement("DELETE FROM app_flags WHERE name LIKE ?")) {
                    p.setString(1, prefix + "%");
                    p.executeUpdate();
                }
            }
        }
        AppFlags.replaceValues(Map.of(ShopSettings.PREFIX, ""));
        ShopSettings.resetForTests();
    }

    private static int count(String sql, String arg) throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, arg);
            try (ResultSet rs = p.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    public void everyValueRoundTripsIncludingAnAddressLongerThanTheOldNameColumn() {
        String longAddress = "x".repeat(200);
        AppFlags.replaceValues(Map.of(ShopSettings.PREFIX, "Quán Mai", ShopSettings.ADDRESS_PREFIX, longAddress,
                ShopSettings.PHONE_PREFIX, "024 3825 1234", ShopSettings.TAX_PREFIX, "0312345678", ShopSettings.LOW_STOCK_PREFIX, "7"));
        assertEquals("Quán Mai", AppFlags.valueWithPrefix(ShopSettings.PREFIX).orElseThrow());
        assertEquals(longAddress, AppFlags.valueWithPrefix(ShopSettings.ADDRESS_PREFIX).orElseThrow());
        assertEquals("024 3825 1234", AppFlags.valueWithPrefix(ShopSettings.PHONE_PREFIX).orElseThrow());
        assertEquals("0312345678", AppFlags.valueWithPrefix(ShopSettings.TAX_PREFIX).orElseThrow());
        assertEquals("7", AppFlags.valueWithPrefix(ShopSettings.LOW_STOCK_PREFIX).orElseThrow());
    }

    @Test
    public void aSecondSaveReplacesAndNeverAddsARow() throws Exception {
        AppFlags.replaceValues(Map.of(ShopSettings.ADDRESS_PREFIX, "first"));
        AppFlags.replaceValues(Map.of(ShopSettings.ADDRESS_PREFIX, "second"));
        assertEquals("second", AppFlags.valueWithPrefix(ShopSettings.ADDRESS_PREFIX).orElseThrow());
        assertEquals(1, count("SELECT COUNT(*) FROM settings WHERE name = ?", "shopaddr"));
    }

    @Test
    public void aBlankValueIsKeptAsBlankNotAsMissing() {
        AppFlags.replaceValues(Map.of(ShopSettings.ADDRESS_PREFIX, ""));
        assertEquals("", AppFlags.valueWithPrefix(ShopSettings.ADDRESS_PREFIX).orElseThrow());
    }

    @Test
    public void anOldFlagIsStillReadWhenNothingWasSavedTheNewWay() throws Exception {
        try (Connection c = DatabaseConfig.getConnection()) {
            try (PreparedStatement p = c.prepareStatement("DELETE FROM settings WHERE name = ?")) {
                p.setString(1, "shoptax");
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO app_flags (name) VALUES (?)")) {
                p.setString(1, ShopSettings.TAX_PREFIX + "0398765432");
                p.executeUpdate();
            }
        }
        assertEquals("0398765432", AppFlags.valueWithPrefix(ShopSettings.TAX_PREFIX).orElseThrow());
    }

    @Test
    public void theNextSaveMovesAnOldFlagIntoTheTableAndRemovesIt() throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO app_flags (name) VALUES (?)")) {
            p.setString(1, ShopSettings.PHONE_PREFIX + "0900000000");
            p.executeUpdate();
        }
        AppFlags.replaceValues(Map.of(ShopSettings.PHONE_PREFIX, "0911111111"));
        assertEquals("0911111111", AppFlags.valueWithPrefix(ShopSettings.PHONE_PREFIX).orElseThrow());
        assertEquals(0, count("SELECT COUNT(*) FROM app_flags WHERE name LIKE ?", ShopSettings.PHONE_PREFIX + "%"));
        assertEquals(1, count("SELECT COUNT(*) FROM settings WHERE name = ?", "shopphone"));
    }

    @Test
    public void aFailureInTheSameTransactionLeavesTheOldValue() {
        AppFlags.replaceValues(Map.of(ShopSettings.PREFIX, "Kept"));
        try {
            AppFlags.replaceValues(Map.of(ShopSettings.PREFIX, "Lost"), connection -> { throw new IllegalStateException("stop"); });
            fail("the extra write failed, so the save must fail");
        } catch (IllegalStateException expected) {
            // rolled back
        }
        assertEquals("Kept", AppFlags.valueWithPrefix(ShopSettings.PREFIX).orElseThrow());
    }

    @Test
    public void theKeyIsThePrefixWithoutItsClosingMark() {
        assertEquals("shopname", AppFlags.keyOf("shopname:"));
        assertEquals("transferqr.pic", AppFlags.keyOf("transferqr.pic="));
        assertEquals("plain", AppFlags.keyOf("plain"));
    }
}
