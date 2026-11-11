package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 2.103: the "VAT and service charge included" line is the owner's own text, off until written; nothing claims it by itself. */
public class PriceNoteTest {

    @Test
    public void aNoteIsCleanedToOneLine() {
        assertEquals("VAT included", PriceNoteText.clean("  VAT   included​ "));
        assertEquals("", PriceNoteText.clean(null));
        assertEquals("", PriceNoteText.clean("   "));
    }

    @Test
    public void aNoteHasALimit() {
        assertNull(PriceNoteText.problem("x".repeat(PriceNoteText.MAX)));
        assertNotNull(PriceNoteText.problem("x".repeat(PriceNoteText.MAX + 1)));
        assertNull("empty switches it off", PriceNoteText.problem(""));
    }

    @Test
    public void theSalesScreenNoLongerHasTheFixedClaim() throws IOException {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/quanlydonhang.fxml"));
        assertFalse("the fixed text is back", fxml.contains("%pos.total.note"));
        assertTrue(fxml.contains("PriceNoteLabel"));
        String settings = Files.readString(Path.of("src/main/java/com/coffeeshop/coffeeshopmanagement/controller/SettingsDialog.java"));
        assertTrue(settings.contains("PriceNote.save(priceNote)"));
    }
}
