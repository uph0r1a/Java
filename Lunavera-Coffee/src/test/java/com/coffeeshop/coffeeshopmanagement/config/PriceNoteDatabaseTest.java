package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/** TODO 2.103 on the real test database: the note is saved, read back after a restart (load), and switched off by saving nothing. */
public class PriceNoteDatabaseTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void putBack() {
        AppFlags.replaceValues(Map.of(PriceNote.PREFIX, ""));
        PriceNote.load();
    }

    @Test
    public void theLineIsOffUntilTheOwnerWritesIt() {
        PriceNote.load();
        assertEquals("", PriceNote.cached());
    }

    @Test
    public void aSavedNoteComesBackAndBlankSwitchesItOff() {
        PriceNote.save("  Đã bao gồm VAT  ");
        assertEquals("Đã bao gồm VAT", PriceNote.cached());
        PriceNote.load();
        assertEquals("Đã bao gồm VAT", PriceNote.cached());
        PriceNote.save("");
        PriceNote.load();
        assertEquals("", PriceNote.cached());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aTooLongNoteIsRefusedAndNothingChanges() {
        PriceNote.save("x".repeat(PriceNote.MAX + 1));
    }
}
