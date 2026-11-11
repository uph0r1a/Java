package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.service.StockLevel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** TODO 2A.44: which of the shop's details count as changed when the settings are saved (no database needed). */
public class ShopSettingsAuditTest {

    @Before
    public void fresh() {
        ShopSettings.resetForTests();
        StockLevel.resetForTests();
    }

    @After
    public void clean() {
        ShopSettings.resetForTests();
        StockLevel.resetForTests();
    }

    @Test
    public void savingTheSameDetailsChangesNothing() {
        assertTrue(ShopSettings.detailChanges(ShopSettings.cachedName(), "", "", "", StockLevel.DEFAULT, "00:00").isEmpty());
    }

    @Test
    public void eachDetailThatDiffersIsOneChangeWithItsOldAndNewText() {
        List<String[]> changes = ShopSettings.detailChanges("Quán Mai", "12 Hàng Bài", "0912345678", "0123456789", 15, "04:00");
        assertEquals(6, changes.size());
        assertArrayEquals(new String[]{ShopSettings.AUDIT_NAME, "Lunavera Coffee", "Quán Mai"}, changes.get(0));
        assertArrayEquals(new String[]{ShopSettings.AUDIT_ADDRESS, "", "12 Hàng Bài"}, changes.get(1));
        assertArrayEquals(new String[]{ShopSettings.AUDIT_PHONE, "", "0912345678"}, changes.get(2));
        assertArrayEquals(new String[]{ShopSettings.AUDIT_TAX_CODE, "", "0123456789"}, changes.get(3));
        assertArrayEquals(new String[]{ShopSettings.AUDIT_LOW_STOCK, "10", "15"}, changes.get(4));
        assertArrayEquals(new String[]{ShopSettings.AUDIT_DAY_START, "00:00", "04:00"}, changes.get(5));
    }

    @Test
    public void onlyTheDetailThatChangedIsListed() {
        List<String[]> changes = ShopSettings.detailChanges(ShopSettings.cachedName(), "", "0912345678", "", StockLevel.DEFAULT, "00:00");
        assertEquals(1, changes.size());
        assertEquals(ShopSettings.AUDIT_PHONE, changes.get(0)[0]);
    }
}
