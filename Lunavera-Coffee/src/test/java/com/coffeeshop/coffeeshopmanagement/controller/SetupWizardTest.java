package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.AppFlags;
import com.coffeeshop.coffeeshopmanagement.config.ShopSettings;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.15: what the setup window accepts and what saving it does. */
public class SetupWizardTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void putBack() {
        TestSession.clear();
        AppFlags.replaceValues(Map.of(ShopSettings.PREFIX, ""));
        AppFlags.clear(AppFlags.SETUP_PENDING);
        ShopSettings.resetForTests();
    }

    private static List<Integer> numbers() {
        return new TableDAO().findAll().stream().map(DiningTable::getTableNumber).toList();
    }

    @Test
    public void aGoodNameAndTableCountPass() {
        assertNull(SetupWizard.problemWith("Quán Mai", "12"));
        assertNull(SetupWizard.problemWith("  Mai ", " 3 "));
    }

    @Test
    public void aBlankNameOrABadTableCountIsRefusedWithAMessage() {
        assertNotNull(SetupWizard.problemWith("", "12"));
        assertNotNull(SetupWizard.problemWith("   ", "12"));
        assertNotNull(SetupWizard.problemWith("Mai", "0"));
        assertNotNull(SetupWizard.problemWith("Mai", "abc"));
        assertNotNull(SetupWizard.problemWith("Mai", ""));
        assertNotNull(SetupWizard.problemWith("x".repeat(ShopSettings.NAME_MAX + 1), "12"));
    }

    @Test
    public void savingWritesTheNameAddsTablesAndClearsThePendingMark() {
        TestSession.admin();
        List<Integer> before = numbers();
        AppFlags.mark(AppFlags.SETUP_PENDING);
        int kept = SetupWizard.apply("  Quán  Mai ", before.size() + 2, before);
        try {
            assertEquals(0, kept);
            assertEquals(before.size() + 2, numbers().size());
            assertEquals("Quán Mai", ShopSettings.cachedName());
            assertFalse(AppFlags.has(AppFlags.SETUP_PENDING));
        } finally {
            SetupWizard.apply("Quán Mai", before.size(), numbers()); // put the shared test tables back
        }
        assertEquals(before.size(), numbers().size());
        assertTrue("the original tables are untouched", numbers().containsAll(before));
    }

    @Test
    public void fewerTablesRemoveTheHighestFreeOnesAndReportTheOnesThatCannotGo() {
        TestSession.admin();
        List<Integer> before = numbers();
        SetupWizard.apply("Mai", before.size() + 2, before);              // two more, numbered above all the others
        List<Integer> with = numbers();
        int kept = SetupWizard.apply("Mai", before.size(), with);
        assertEquals(0, kept);
        assertEquals(before, numbers());
    }
}
